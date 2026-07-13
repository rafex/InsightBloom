package dev.rafex.insightbloom.users.domain.model;

import java.time.Instant;
import java.util.UUID;

public class Sandbox {
    private final String uuid;
    private final String conferenceUuid;
    private final int sandboxSlot; // 0 a (pool_size - 1)
    private final String userUuid;
    private final Instant assignedAt;
    private final Instant createdAt;
    private final Instant expiresAt; // basado en Conference.expiresAt + ttlSecondsAfterEventExpiry

    /** Fase 3: el Pod se crea junto con la fila, ya asignado — no hay "slot vacío" intermedio. */
    public Sandbox(final String conferenceUuid, final int sandboxSlot, final String userUuid, final Instant expiresAt) {
        this.uuid = UUID.randomUUID().toString();
        this.conferenceUuid = conferenceUuid;
        this.sandboxSlot = sandboxSlot;
        this.userUuid = userUuid;
        this.assignedAt = Instant.now();
        this.createdAt = Instant.now();
        this.expiresAt = expiresAt;
    }

    public Sandbox(final String uuid, final String conferenceUuid, final int sandboxSlot,
                    final String userUuid, final Instant assignedAt,
                    final Instant createdAt, final Instant expiresAt) {
        this.uuid = uuid;
        this.conferenceUuid = conferenceUuid;
        this.sandboxSlot = sandboxSlot;
        this.userUuid = userUuid;
        this.assignedAt = assignedAt;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public String getUuid() { return uuid; }
    public String getConferenceUuid() { return conferenceUuid; }
    public int getSandboxSlot() { return sandboxSlot; }
    public String getUserUuid() { return userUuid; }
    public Instant getAssignedAt() { return assignedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }

    /** Nombre determinístico del recurso K8s (Pod+Service) — no se persiste, se recalcula siempre. */
    public String podName() {
        return podName(conferenceUuid, sandboxSlot);
    }

    public static String podName(final String conferenceUuid, final int sandboxSlot) {
        final String compact = conferenceUuid.replace("-", "");
        return "sandbox-" + compact.substring(0, Math.min(8, compact.length())) + "-" + sandboxSlot;
    }
}
