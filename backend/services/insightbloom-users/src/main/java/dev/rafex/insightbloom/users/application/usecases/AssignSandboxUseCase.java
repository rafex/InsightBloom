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

        final var existing = sandboxRepository.findByConferenceAndUser(conferenceUuid, userUuid);
        if (existing.isPresent()) {
            final Sandbox sandbox = existing.get();
            // La fila puede sobrevivir a la borrada manual/eviccion del Pod real (ej. purga de un
            // Pod roto durante un incidente) -- sin este chequeo, GetSandbox devolveria PENDING
            // para siempre porque nada vuelve a crear el Pod. getPhase()==null significa que el
            // Pod no existe; se recrea con el mismo nombre/slot, sin tocar la fila (ya es correcta).
            if (sandboxOrchestrator.getPhase(sandbox.podName()) == null) {
                recreatePod(sandbox, conference);
            }
            return sandbox;
        }

        final var unassigned = sandboxRepository.findUnassigned(conferenceUuid);
        if (unassigned.isPresent()) {
            final Sandbox free = unassigned.get();
            final Instant assignedAt = Instant.now();
            if (sandboxRepository.claim(free.getUuid(), userUuid, assignedAt)) {
                return new Sandbox(free.getUuid(), conferenceUuid, free.getSandboxSlot(), userUuid,
                    assignedAt, free.getCreatedAt(), free.getExpiresAt());
            }
            // Perdio la carrera por ese slot (otro request lo reclamo primero) -- sigue abajo
            // como si no hubiera habido un sandbox libre.
        }

        final List<Sandbox> active = sandboxRepository.findByConferenceUuid(conferenceUuid);
        final int poolSize = conference.getSandboxPoolSize() != null ? conference.getSandboxPoolSize() : DEFAULT_POOL_SIZE;
        if (active.size() >= poolSize) {
            throw new IllegalArgumentException("sandbox_pool_full");
        }

        final int slot = nextFreeSlot(active, poolSize);
        final String variant = conference.getSandboxVariant() != null ? conference.getSandboxVariant() : DEFAULT_VARIANT;
        final boolean internetEnabled = conference.getSandboxInternetEnabled() != null
            && conference.getSandboxInternetEnabled() == 1;
        final Instant expiresAt = conference.getExpiresAt() != null
            ? conference.getExpiresAt().plusSeconds(ttlSecondsAfterEventExpiry)
            : Instant.now().plusSeconds(DEFAULT_TTL_SECONDS);

        final Sandbox sandbox = new Sandbox(conferenceUuid, slot, userUuid, expiresAt);

        try {
            sandboxOrchestrator.createSandbox(sandbox.podName(), conferenceUuid, variant,
                conference.getSandboxExtraPackages(), conference.getSandboxRemoteGitUrl(), internetEnabled);
        } catch (final IllegalStateException e) {
            if ("kubernetes_not_configured".equals(e.getMessage())) {
                throw new IllegalArgumentException("sandbox_unavailable");
            }
            throw e;
        }

        try {
            sandboxRepository.save(sandbox);
        } catch (final RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("UNIQUE")) {
                sandboxOrchestrator.deleteSandbox(sandbox.podName());
                throw new IllegalArgumentException("sandbox_pool_full");
            }
            throw e;
        }

        return sandbox;
    }

    private void recreatePod(final Sandbox sandbox, final Conference conference) {
        final String variant = conference.getSandboxVariant() != null ? conference.getSandboxVariant() : DEFAULT_VARIANT;
        final boolean internetEnabled = conference.getSandboxInternetEnabled() != null
            && conference.getSandboxInternetEnabled() == 1;
        try {
            sandboxOrchestrator.createSandbox(sandbox.podName(), sandbox.getConferenceUuid(), variant,
                conference.getSandboxExtraPackages(), conference.getSandboxRemoteGitUrl(), internetEnabled);
        } catch (final IllegalStateException e) {
            if ("kubernetes_not_configured".equals(e.getMessage())) {
                throw new IllegalArgumentException("sandbox_unavailable");
            }
            throw e;
        }
    }

    private static int nextFreeSlot(final List<Sandbox> active, final int poolSize) {
        final boolean[] taken = new boolean[poolSize];
        for (final Sandbox s : active) {
            if (s.getSandboxSlot() >= 0 && s.getSandboxSlot() < poolSize) {
                taken[s.getSandboxSlot()] = true;
            }
        }
        for (int i = 0; i < poolSize; i++) {
            if (!taken[i]) return i;
        }
        throw new IllegalArgumentException("sandbox_pool_full");
    }
}
