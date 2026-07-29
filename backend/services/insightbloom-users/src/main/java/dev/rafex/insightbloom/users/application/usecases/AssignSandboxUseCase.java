package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.Conference;
import dev.rafex.insightbloom.users.domain.model.Sandbox;
import dev.rafex.insightbloom.users.domain.model.ToolKind;
import dev.rafex.insightbloom.users.domain.ports.ConferenceRepository;
import dev.rafex.insightbloom.users.domain.ports.SandboxOrchestrator;
import dev.rafex.insightbloom.users.domain.ports.SandboxRepository;
import dev.rafex.insightbloom.users.domain.services.DeviceAccessGuard;
import dev.rafex.insightbloom.users.domain.services.DeviceBlockedException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class AssignSandboxUseCase {
    private static final int DEFAULT_POOL_SIZE = 1;
    private static final long DEFAULT_TTL_SECONDS = 4 * 3600; // sin fecha de evento: 4h desde ahora
    /** Ver KubernetesPodClient.IDE_MODE_TERMINAL_NVIM -- mismo sentinel, mismo significado. */
    private static final String IDE_MODE_TERMINAL_NVIM = "terminal-nvim";
    /** Variant que se le pasa al orquestador para el pool "web" -- cualquier valor distinto de
     *  IDE_MODE_TERMINAL_NVIM selecciona la imagen debian/code-server (ver KubernetesPodClient). */
    private static final String ORCHESTRATOR_VARIANT_WEB = "python";
    private static final int DEFAULT_SEATS_PER_POD = 4;

    private final SandboxRepository sandboxRepository;
    private final ConferenceRepository conferenceRepository;
    private final SandboxOrchestrator sandboxOrchestrator;
    private final DeviceAccessGuard deviceAccessGuard;
    private final long ttlSecondsAfterEventExpiry;

    public AssignSandboxUseCase(final SandboxRepository sandboxRepository,
                                 final ConferenceRepository conferenceRepository,
                                 final SandboxOrchestrator sandboxOrchestrator,
                                 final DeviceAccessGuard deviceAccessGuard,
                                 final long ttlSecondsAfterEventExpiry) {
        this.sandboxRepository = sandboxRepository;
        this.conferenceRepository = conferenceRepository;
        this.sandboxOrchestrator = sandboxOrchestrator;
        this.deviceAccessGuard = deviceAccessGuard;
        this.ttlSecondsAfterEventExpiry = ttlSecondsAfterEventExpiry;
    }

    /**
     * Traduce la variante de DOMINIO ({@link Sandbox#VARIANT_WEB}/{@link Sandbox#VARIANT_CLI})
     * al string que espera {@link SandboxOrchestrator} (contrato viejo, sin tocar): cualquier
     * valor distinto de {@code "terminal-nvim"} selecciona la imagen debian/code-server.
     */
    private static String toOrchestratorVariant(final String domainVariant) {
        return Sandbox.VARIANT_CLI.equals(domainVariant) ? IDE_MODE_TERMINAL_NVIM : ORCHESTRATOR_VARIANT_WEB;
    }

    private static int seatsPerPodFor(final String domainVariant, final Conference conference) {
        // Solo el pool "cli" (terminal-nvim) admite compartir Pod entre alumnos -- "web"
        // (code-server) siempre es 1 asiento por pod, no se puede compartir.
        if (!Sandbox.VARIANT_CLI.equals(domainVariant)) {
            return 1;
        }
        return conference.getSandboxSeatsPerPod() != null ? conference.getSandboxSeatsPerPod() : DEFAULT_SEATS_PER_POD;
    }

    private static int poolSizeFor(final String domainVariant, final Conference conference) {
        final Integer configured = Sandbox.VARIANT_CLI.equals(domainVariant)
                ? conference.getSandboxCliPoolSize() : conference.getSandboxPoolSize();
        return configured != null ? configured : DEFAULT_POOL_SIZE;
    }

    private Instant computeExpiresAt(final Conference conference) {
        return conference.getExpiresAt() != null
            ? conference.getExpiresAt().plusSeconds(ttlSecondsAfterEventExpiry)
            : Instant.now().plusSeconds(DEFAULT_TTL_SECONDS);
    }

    /**
     * Provisiona (o reusa) el sandbox del usuario para este evento, en la variante pedida
     * ({@link Sandbox#VARIANT_WEB} o {@link Sandbox#VARIANT_CLI}).
     *
     * Pools independientes (2026-07): Web y CLI conviven siempre para toda conferencia con
     * CODE_IDE habilitada, cada uno con su propio tamaño de pool
     * ({@code Conference.sandboxPoolSize}/{@code sandboxCliPoolSize}) y su propia numeracion de
     * slot -- ver {@link Sandbox#podName()}, que ahora incluye la variante en el nombre del Pod
     * justamente para que las dos numeraciones no choquen.
     *
     * Un alumno tiene UN SOLO sandbox activo a la vez: si ya tiene uno de una variante distinta
     * a la pedida, se libera (Pod + fila) antes de crear/reclamar el de la variante nueva —
     * ver {@link #freeExisting(Sandbox)}.
     *
     * Concurrencia: {@link SandboxRepository#save} hace un INSERT real (no upsert) contra
     * UNIQUE(conference_uuid, variant, sandbox_slot, seat_index) — si dos requests calculan el
     * mismo slot libre en paralelo, uno de los dos INSERT falla y se traduce a
     * {@code sandbox_pool_full} (el usuario puede reintentar).
     *
     * Si {@link EnsureUnassignedSandboxUseCase} ya dejó un sandbox libre (sin usuario) de la
     * MISMA variante esperando, se reclama de inmediato sin golpear el API de Kubernetes.
     */
    public Sandbox execute(final String conferenceUuid, final String userUuid, final String requestedVariant) {
        return execute(conferenceUuid, userUuid, requestedVariant, null);
    }

    public Sandbox execute(final String conferenceUuid, final String userUuid, final String requestedVariant,
                            final String deviceFingerprint) {
        final Conference conference = conferenceRepository.findByUuid(conferenceUuid)
            .orElseThrow(() -> new IllegalArgumentException("conference_not_found"));
        // deviceFingerprint puede llegar null durante un rollout escalonado (frontend viejo sin
        // el header todavia) -- en ese caso se omite el control de acceso por dispositivo en vez
        // de romper la asignacion del sandbox.
        if (deviceFingerprint != null && !deviceFingerprint.isBlank()) {
            final var access = deviceAccessGuard.checkAndRegister(
                    conferenceUuid, userUuid, ToolKind.IDE, deviceFingerprint, conference);
            if (access instanceof DeviceAccessGuard.DeviceAccessResult.Blocked) {
                throw new DeviceBlockedException();
            }
        }
        final String variant = Sandbox.VARIANT_CLI.equals(requestedVariant) ? Sandbox.VARIANT_CLI : Sandbox.VARIANT_WEB;
        final String orchestratorVariant = toOrchestratorVariant(variant);
        final int seatsPerPod = seatsPerPodFor(variant, conference);

        final var existing = sandboxRepository.findByConferenceAndUser(conferenceUuid, userUuid);
        if (existing.isPresent()) {
            final Sandbox sandbox = existing.get();
            if (!variant.equals(sandbox.getVariant())) {
                // El alumno pidio la otra variante -- un solo sandbox activo a la vez, se libera
                // el actual antes de seguir con la asignacion normal de la variante nueva.
                freeExisting(sandbox);
            } else {
                // La fila puede sobrevivir a la borrada manual/eviccion del Pod real (ej. purga
                // de un Pod roto durante un incidente) -- sin este chequeo, GetSandbox devolveria
                // PENDING para siempre porque nada vuelve a crear el Pod. getPhase()==null
                // significa que el Pod no existe; se recrea con el mismo nombre/slot.
                if (sandboxOrchestrator.getPhase(sandbox.podName()) == null) {
                    recreatePod(sandbox, conference, orchestratorVariant);
                }
                if (seatsPerPod > 1) {
                    // Intento best-effort en CADA reconexion (no solo cuando el Pod estaba
                    // totalmente ausente): idempotente del lado del seat-agent, y es lo que
                    // permite auto-sanar el caso real donde el Pod existe y esta "Ready" pero el
                    // intento ORIGINAL de aprovisionar este asiento nunca llego a completarse
                    // (cold start mas lento que el primer intento) -- antes de esto, ese estado
                    // quedaba atascado para siempre y requeria reaprovisionar el asiento a mano.
                    // Nunca lanza excepcion (ver ensureSeatReady); si falla, el status que
                    // devuelve el handler queda en PENDING y el siguiente poll del frontend
                    // reintenta solo, sin intervencion.
                    sandboxOrchestrator.ensureSeatReady(sandbox.podName(), sandbox.getSeatIndex(), userUuid);
                }
                // Refresca el vencimiento en cada reconexion -- sin esto, una sesion en uso
                // activo igual expiraba a las N horas de su PRIMERA creacion (incidente
                // 2026-07-19: el sandbox desaparecia a mitad de una sesion larga, sin relacion
                // con inactividad real). Eventos sin expiresAt fijo (la mayoria) quedan con una
                // ventana rodante desde el ultimo uso; eventos con expiresAt fijo recalculan al
                // mismo valor de siempre (idempotente).
                final Instant refreshedExpiresAt = computeExpiresAt(conference);
                sandboxRepository.updateExpiresAt(sandbox.getUuid(), refreshedExpiresAt);
                return new Sandbox(sandbox.getUuid(), sandbox.getConferenceUuid(), sandbox.getSandboxSlot(),
                        sandbox.getSeatIndex(), sandbox.getVariant(), sandbox.getUserUuid(), sandbox.getAssignedAt(),
                        sandbox.getCreatedAt(), refreshedExpiresAt);
            }
        }

        // El repositorio histórico devuelve el primer sandbox libre de cualquier variante.
        // Si ese primero pertenece al otro pool, buscamos también en las filas de la variante
        // solicitada; de lo contrario un Pod Web precalentado podía ocultar un Pod CLI libre.
        var unassigned = sandboxRepository.findUnassigned(conferenceUuid)
            .filter(s -> variant.equals(s.getVariant()));
        if (unassigned.isEmpty()) {
            unassigned = sandboxRepository.findByConferenceUuid(conferenceUuid).stream()
                .filter(s -> variant.equals(s.getVariant()) && s.getUserUuid() == null)
                .findFirst();
        }
        if (unassigned.isPresent()) {
            final Sandbox free = unassigned.get();
            final Instant assignedAt = Instant.now();
            if (sandboxRepository.claim(free.getUuid(), userUuid, assignedAt)) {
                final Sandbox claimed = new Sandbox(free.getUuid(), conferenceUuid, free.getSandboxSlot(),
                        free.getSeatIndex(), variant, userUuid, assignedAt, free.getCreatedAt(), free.getExpiresAt());
                if (seatsPerPod > 1) {
                    // El Pod ya existia (pre-warmed por EnsureUnassignedSandboxUseCase) pero sin
                    // usuario real asignado todavia -- el seat-agent recien ahora se entera de
                    // QUIEN ocupa este asiento y crea su usuario Linux/ttyd. Best-effort (ver
                    // comentario en la rama de reconexion mas arriba): si el Pod recien esta
                    // arrancando, isSeatFullyProvisioned lo reintenta en cada poll de status.
                    sandboxOrchestrator.ensureSeatReady(claimed.podName(), claimed.getSeatIndex(), userUuid);
                }
                return claimed;
            }
            // Perdio la carrera por ese slot (otro request lo reclamo primero) -- sigue abajo
            // como si no hubiera habido un sandbox libre.
        }

        // Las filas sin usuario son Pods listos, no ocupantes. Solo las asignaciones reales
        // consumen capacidad y participan en el cálculo de asientos.
        final List<Sandbox> activeInVariant = new ArrayList<>();
        for (final Sandbox s : sandboxRepository.findByConferenceUuid(conferenceUuid)) {
            if (variant.equals(s.getVariant()) && s.getUserUuid() != null) {
                activeInVariant.add(s);
            }
        }
        final int poolSize = poolSizeFor(variant, conference);
        if (activeInVariant.size() >= poolSize * seatsPerPod) {
            throw new IllegalArgumentException("sandbox_pool_full");
        }

        final Allocation allocation = nextFreeSeat(activeInVariant, poolSize, seatsPerPod);
        final boolean internetEnabled = conference.getSandboxInternetEnabled() != null
            && conference.getSandboxInternetEnabled() == 1;
        final Instant expiresAt = computeExpiresAt(conference);

        final Sandbox sandbox = new Sandbox(conferenceUuid, allocation.slot(), allocation.seatIndex(), variant,
                userUuid, expiresAt);

        if (allocation.isNewPod()) {
            // Asiento 0 de un slot que todavia no tiene Pod -- hay que crearlo. Si el slot ya
            // tiene otros alumnos (asiento > 0 de un Pod compartido existente), el Pod ya esta
            // corriendo: no hace falta (ni conviene) volver a pedirlo, solo sumar el asiento
            // (ver mas abajo).
            try {
                sandboxOrchestrator.createSandbox(sandbox.podName(), conferenceUuid, orchestratorVariant,
                    conference.getSandboxRemoteGitUrl(), internetEnabled,
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
            // ningun asiento por si solo. Best-effort y rapido a proposito (no bloquea la
            // respuesta esperando a que el Pod recien creado termine de arrancar, que puede
            // tardar minutos) -- isSeatFullyProvisioned reintenta en cada poll de status
            // (GetSandbox), mismo mecanismo de auto-sanado que la rama de reconexion.
            sandboxOrchestrator.ensureSeatReady(sandbox.podName(), allocation.seatIndex(), userUuid);
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
     * Usado por {@code SandboxHandler} para calcular el status ("READY"/"PENDING") que ve el
     * frontend -- a diferencia de {@code SandboxOrchestrator.isReady} (que solo mira si el Pod
     * paso su readiness probe), esto TAMBIEN confirma que el asiento especifico de este alumno
     * ya fue provisionado en el seat-agent cuando el Pod es multi-asiento (terminal-nvim
     * compartido). Sin este chequeo extra, el frontend podia recibir READY con el Pod sano pero
     * el asiento 0 todavia sin usuario Linux/ttyd -- se veia como IDE "listo" que en realidad
     * daba 502/conexion rechazada al abrirlo. Vuelve a intentar el aprovisionamiento (idempotente,
     * rapido) en cada llamada -- ver DEC-0027.
     */
    public boolean isSeatFullyProvisioned(final Sandbox sandbox, final String userUuid) {
        if (!Sandbox.VARIANT_CLI.equals(sandbox.getVariant())) {
            return true; // Web: un asiento por Pod, sin seat-agent -- isReady(Pod) alcanza.
        }
        final Conference conference = conferenceRepository.findByUuid(sandbox.getConferenceUuid()).orElse(null);
        final int seatsPerPod = conference != null ? seatsPerPodFor(Sandbox.VARIANT_CLI, conference) : DEFAULT_SEATS_PER_POD;
        if (seatsPerPod <= 1) {
            return true; // Pod terminal-nvim de un solo asiento: mismo caso que Web.
        }
        return sandboxOrchestrator.ensureSeatReady(sandbox.podName(), sandbox.getSeatIndex(), userUuid);
    }

    /**
     * Libera el sandbox previo del alumno al cambiar de variante (un alumno tiene un solo
     * sandbox activo a la vez, ver javadoc de {@link #execute}). Si era el ultimo ocupante de
     * un Pod compartido, el Pod igual se borra entero (misma politica simple que
     * {@code PurgeSandboxPoolUseCase}: no vale la pena mantener un Pod "cli" vivo con cero
     * alumnos reales solo por si alguien mas se une despues del cambio de variante).
     */
    private void freeExisting(final Sandbox sandbox) {
        try {
            sandboxOrchestrator.deleteSandbox(sandbox.podName());
        } catch (final RuntimeException ignored) {
            // Best-effort: si el Pod ya no existe o Kubernetes no esta configurado (dev local),
            // igual liberamos la fila -- no debe bloquear al alumno cambiando de variante.
        }
        sandboxRepository.deleteByUuid(sandbox.getUuid());
    }

    /**
     * Recrea el Pod cuando ya no existe (ver caller, que tambien llama
     * {@code provisionSeat} para este asiento despues). Nota Pod compartido: si
     * {@code sandbox} es un asiento &gt; 0 de un Pod que muere, esto recrea el Pod de cero
     * (createSandbox es idempotente) pero solo re-provisiona a ESTE usuario -- los demas
     * asientos que compartian ese Pod quedan con su fila en la BD pero sin usuario Linux/ttyd
     * real hasta que ellos tambien vuelvan a pasar por este flujo.
     */
    private void recreatePod(final Sandbox sandbox, final Conference conference, final String orchestratorVariant) {
        final boolean internetEnabled = conference.getSandboxInternetEnabled() != null
            && conference.getSandboxInternetEnabled() == 1;
        try {
            sandboxOrchestrator.createSandbox(sandbox.podName(), sandbox.getConferenceUuid(), orchestratorVariant,
                conference.getSandboxRemoteGitUrl(), internetEnabled,
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
            if (s.getUserUuid() == null) continue;
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
