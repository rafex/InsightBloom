package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.Conference;
import dev.rafex.insightbloom.users.domain.model.Sandbox;
import dev.rafex.insightbloom.users.domain.ports.ConferenceRepository;
import dev.rafex.insightbloom.users.domain.ports.SandboxOrchestrator;
import dev.rafex.insightbloom.users.domain.ports.SandboxRepository;

import java.time.Instant;
import java.util.List;

public class AssignSandboxUseCase {
    private static final int DEFAULT_POOL_SIZE = 1;
    private static final String DEFAULT_VARIANT = "python";
    private static final long DEFAULT_TTL_SECONDS = 4 * 3600; // sin fecha de evento: 4h desde ahora
    /** Ver KubernetesPodClient.IDE_MODE_TERMINAL_NVIM -- mismo sentinel, mismo significado. */
    private static final String IDE_MODE_TERMINAL_NVIM = "terminal-nvim";
    private static final int DEFAULT_SEATS_PER_POD = 4;

    private final SandboxRepository sandboxRepository;
    private final ConferenceRepository conferenceRepository;
    private final SandboxOrchestrator sandboxOrchestrator;
    private final long ttlSecondsAfterEventExpiry;

    public AssignSandboxUseCase(final SandboxRepository sandboxRepository,
                                 final ConferenceRepository conferenceRepository,
                                 final SandboxOrchestrator sandboxOrchestrator,
                                 final long ttlSecondsAfterEventExpiry) {
        this.sandboxRepository = sandboxRepository;
        this.conferenceRepository = conferenceRepository;
        this.sandboxOrchestrator = sandboxOrchestrator;
        this.ttlSecondsAfterEventExpiry = ttlSecondsAfterEventExpiry;
    }

    /**
     * Provisiona (o reusa) el sandbox del usuario para este evento.
     *
     * Fase 3: ya no hay un pool de slots pre-sembrados — el Pod se crea la primera vez que un
     * usuario lo pide, hasta {@code Conference.sandboxPoolSize} sandboxes concurrentes por evento
     * (default 1). Si el usuario ya tiene uno asignado, se reusa (idempotente ante recarga de
     * página) sin volver a golpear el API de Kubernetes — salvo que el Pod ya no exista (ver
     * abajo), en cuyo caso se recrea con el mismo nombre/slot.
     *
     * Concurrencia: {@link SandboxRepository#save} hace un INSERT real (no upsert) contra
     * UNIQUE(conference_uuid, sandbox_slot) — si dos requests calculan el mismo slot libre en
     * paralelo, uno de los dos INSERT falla y se traduce a {@code sandbox_pool_full} (el usuario
     * puede reintentar; para entonces el otro ya ocupó el slot y el conteo de activos ya no
     * coincide, evitando un loop infinito).
     *
     * Si {@link EnsureUnassignedSandboxUseCase} ya dejó un sandbox libre (sin usuario) esperando,
     * se reclama de inmediato sin golpear el API de Kubernetes — el Pod ya está creado (Pending o
     * Running) desde antes de que este usuario lo pidiera.
     */
    public Sandbox execute(final String conferenceUuid, final String userUuid) {
        final Conference conference = conferenceRepository.findByUuid(conferenceUuid)
            .orElseThrow(() -> new IllegalArgumentException("conference_not_found"));
        final String variant = conference.getSandboxVariant() != null ? conference.getSandboxVariant() : DEFAULT_VARIANT;
        // Solo modo terminal-nvim admite compartir Pod entre alumnos (code-server no se puede
        // compartir) -- en cualquier otro modo, 1 asiento por pod, comportamiento identico al de
        // siempre (ver javadoc de la clase para el porque de esta distincion).
        final int seatsPerPod = IDE_MODE_TERMINAL_NVIM.equals(variant)
                ? (conference.getSandboxSeatsPerPod() != null ? conference.getSandboxSeatsPerPod() : DEFAULT_SEATS_PER_POD)
                : 1;

        final var existing = sandboxRepository.findByConferenceAndUser(conferenceUuid, userUuid);
        if (existing.isPresent()) {
            final Sandbox sandbox = existing.get();
            // La fila puede sobrevivir a la borrada manual/eviccion del Pod real (ej. purga de un
            // Pod roto durante un incidente) -- sin este chequeo, GetSandbox devolveria PENDING
            // para siempre porque nada vuelve a crear el Pod. getPhase()==null significa que el
            // Pod no existe; se recrea con el mismo nombre/slot, sin tocar la fila (ya es correcta).
            if (sandboxOrchestrator.getPhase(sandbox.podName()) == null) {
                recreatePod(sandbox, conference);
                if (seatsPerPod > 1) {
                    sandboxOrchestrator.provisionSeat(sandbox.podName(), sandbox.getSeatIndex(), userUuid);
                }
            }
            return sandbox;
        }

        final var unassigned = sandboxRepository.findUnassigned(conferenceUuid);
        if (unassigned.isPresent()) {
            final Sandbox free = unassigned.get();
            final Instant assignedAt = Instant.now();
            if (sandboxRepository.claim(free.getUuid(), userUuid, assignedAt)) {
                final Sandbox claimed = new Sandbox(free.getUuid(), conferenceUuid, free.getSandboxSlot(),
                        free.getSeatIndex(), userUuid, assignedAt, free.getCreatedAt(), free.getExpiresAt());
                if (seatsPerPod > 1) {
                    // El Pod ya existia (pre-warmed por EnsureUnassignedSandboxUseCase) pero sin
                    // usuario real asignado todavia -- el seat-agent recien ahora se entera de
                    // QUIEN ocupa este asiento y crea su usuario Linux/ttyd.
                    sandboxOrchestrator.provisionSeat(claimed.podName(), claimed.getSeatIndex(), userUuid);
                }
                return claimed;
            }
            // Perdio la carrera por ese slot (otro request lo reclamo primero) -- sigue abajo
            // como si no hubiera habido un sandbox libre.
        }

        final List<Sandbox> active = sandboxRepository.findByConferenceUuid(conferenceUuid);
        final int poolSize = conference.getSandboxPoolSize() != null ? conference.getSandboxPoolSize() : DEFAULT_POOL_SIZE;
        if (active.size() >= poolSize * seatsPerPod) {
            throw new IllegalArgumentException("sandbox_pool_full");
        }

        final Allocation allocation = nextFreeSeat(active, poolSize, seatsPerPod);
        final boolean internetEnabled = conference.getSandboxInternetEnabled() != null
            && conference.getSandboxInternetEnabled() == 1;
        final Instant expiresAt = conference.getExpiresAt() != null
            ? conference.getExpiresAt().plusSeconds(ttlSecondsAfterEventExpiry)
            : Instant.now().plusSeconds(DEFAULT_TTL_SECONDS);

        final Sandbox sandbox = new Sandbox(conferenceUuid, allocation.slot(), allocation.seatIndex(), userUuid, expiresAt);

        if (allocation.isNewPod()) {
            // Asiento 0 de un slot que todavia no tiene Pod -- hay que crearlo. Si el slot ya
            // tiene otros alumnos (asiento > 0 de un Pod compartido existente), el Pod ya esta
            // corriendo: no hace falta (ni conviene) volver a pedirlo, solo sumar el asiento
            // (ver mas abajo).
            try {
                sandboxOrchestrator.createSandbox(sandbox.podName(), conferenceUuid, variant,
                    conference.getSandboxExtraPackages(), conference.getSandboxRemoteGitUrl(), internetEnabled,
                    conference.getSandboxJvmHeapMb(), conference.getSandboxSeatsPerPod());
            } catch (final IllegalStateException e) {
                if ("kubernetes_not_configured".equals(e.getMessage())) {
                    throw new IllegalArgumentException("sandbox_unavailable");
                }
                throw e;
            }
        }
        if (seatsPerPod > 1) {
            // Tanto si el Pod es nuevo (asiento 0) como si nos unimos a uno existente (asiento
            // > 0), el seat-agent necesita el pedido explicito para crear el usuario Linux/ttyd
            // de ESTE asiento -- createSandbox solo deja el Pod+agente corriendo, no aprovisiona
            // ningun asiento por si solo.
            sandboxOrchestrator.provisionSeat(sandbox.podName(), allocation.seatIndex(), userUuid);
        }

        try {
            sandboxRepository.save(sandbox);
        } catch (final RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("UNIQUE")) {
                if (allocation.isNewPod()) {
                    sandboxOrchestrator.deleteSandbox(sandbox.podName());
                }
                throw new IllegalArgumentException("sandbox_pool_full");
            }
            throw e;
        }

        return sandbox;
    }

    /**
     * Recrea el Pod cuando ya no existe (ver caller, que tambien llama
     * {@code provisionSeat} para este asiento despues). Nota Pod compartido: si
     * {@code sandbox} es un asiento &gt; 0 de un Pod que muere, esto recrea el Pod de cero
     * (createSandbox es idempotente) pero solo re-provisiona a ESTE usuario -- los demas
     * asientos que compartian ese Pod quedan con su fila en la BD pero sin usuario Linux/ttyd
     * real hasta que ellos tambien vuelvan a pasar por este flujo.
     */
    private void recreatePod(final Sandbox sandbox, final Conference conference) {
        final String variant = conference.getSandboxVariant() != null ? conference.getSandboxVariant() : DEFAULT_VARIANT;
        final boolean internetEnabled = conference.getSandboxInternetEnabled() != null
            && conference.getSandboxInternetEnabled() == 1;
        try {
            sandboxOrchestrator.createSandbox(sandbox.podName(), sandbox.getConferenceUuid(), variant,
                conference.getSandboxExtraPackages(), conference.getSandboxRemoteGitUrl(), internetEnabled,
                conference.getSandboxJvmHeapMb(), conference.getSandboxSeatsPerPod());
        } catch (final IllegalStateException e) {
            if ("kubernetes_not_configured".equals(e.getMessage())) {
                throw new IllegalArgumentException("sandbox_unavailable");
            }
            throw e;
        }
    }

    /** @param isNewPod true si el asiento 0 de un slot sin Pod todavia -- hay que crear el Pod. */
    private record Allocation(int slot, int seatIndex, boolean isNewPod) {
    }

    /**
     * Primero intenta sumar un asiento a un Pod ya existente con lugar libre (seatsPerPod > 1,
     * modo terminal-nvim compartido) antes de abrir un Pod nuevo -- maximiza el reuso de Pods ya
     * corriendo. Con seatsPerPod == 1 (el caso de siempre, cualquier otro modo) esto se reduce
     * exactamente al comportamiento anterior: cada slot admite un unico ocupante.
     */
    private static Allocation nextFreeSeat(final List<Sandbox> active, final int poolSize, final int seatsPerPod) {
        final int[] occupiedCount = new int[poolSize];
        final boolean[][] seatTaken = new boolean[poolSize][seatsPerPod];
        for (final Sandbox s : active) {
            final int slot = s.getSandboxSlot();
            final int seat = s.getSeatIndex();
            if (slot < 0 || slot >= poolSize) continue;
            occupiedCount[slot]++;
            if (seat >= 0 && seat < seatsPerPod) {
                seatTaken[slot][seat] = true;
            }
        }
        // Paso 1: unirse a un Pod que ya existe (tiene al menos un ocupante) y tiene lugar.
        for (int slot = 0; slot < poolSize; slot++) {
            if (occupiedCount[slot] > 0 && occupiedCount[slot] < seatsPerPod) {
                for (int seat = 0; seat < seatsPerPod; seat++) {
                    if (!seatTaken[slot][seat]) {
                        return new Allocation(slot, seat, false);
                    }
                }
            }
        }
        // Paso 2: abrir un Pod nuevo en el primer slot totalmente libre.
        for (int slot = 0; slot < poolSize; slot++) {
            if (occupiedCount[slot] == 0) {
                return new Allocation(slot, 0, true);
            }
        }
        throw new IllegalArgumentException("sandbox_pool_full");
    }
}
