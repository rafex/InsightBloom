package dev.rafex.insightbloom.users.domain.model;

import java.time.Instant;
import java.util.UUID;

public class Sandbox {
    private final String uuid;
    private final String conferenceUuid;
    private final int sandboxSlot; // 0 a (pool_size - 1) -- identifica el POD, no el alumno
    // Asiento dentro del pod, 0 a (seatsPerPod - 1) -- solo relevante en modo "neovim" con
    // seatsPerPod > 1 (varios alumnos comparten el mismo sandboxSlot/pod, cada uno en su
    // asiento). En modo debian, o neovim con seatsPerPod == 1, siempre vale 0 -- comportamiento
    // identico al de antes de este campo (ver seatPort()).
    private final int seatIndex;
    private final String userUuid;
    private final Instant assignedAt;
    private final Instant createdAt;
    private final Instant expiresAt; // basado en Conference.expiresAt + ttlSecondsAfterEventExpiry

    /**
     * Fase 3: el Pod se crea junto con la fila. {@code userUuid} nulo representa un sandbox
     * pre-provisionado (EnsureUnassignedSandboxUseCase) esperando a que alguien lo reclame —
     * en ese caso {@code assignedAt} queda nulo hasta el reclamo (ver
     * {@link dev.rafex.insightbloom.users.domain.ports.SandboxRepository#claim}).
     */
    public Sandbox(final String conferenceUuid, final int sandboxSlot, final String userUuid, final Instant expiresAt) {
        this(conferenceUuid, sandboxSlot, 0, userUuid, expiresAt);
    }

    /** Variante multi-asiento -- ver {@link #seatIndex}. */
    public Sandbox(final String conferenceUuid, final int sandboxSlot, final int seatIndex,
                    final String userUuid, final Instant expiresAt) {
        this.uuid = UUID.randomUUID().toString();
        this.conferenceUuid = conferenceUuid;
        this.sandboxSlot = sandboxSlot;
        this.seatIndex = seatIndex;
        this.userUuid = userUuid;
        this.assignedAt = userUuid != null ? Instant.now() : null;
        this.createdAt = Instant.now();
        this.expiresAt = expiresAt;
    }

    public Sandbox(final String uuid, final String conferenceUuid, final int sandboxSlot,
                    final String userUuid, final Instant assignedAt,
                    final Instant createdAt, final Instant expiresAt) {
        this(uuid, conferenceUuid, sandboxSlot, 0, userUuid, assignedAt, createdAt, expiresAt);
    }

    /** Variante multi-asiento (reconstruccion desde persistencia) -- ver {@link #seatIndex}. */
    public Sandbox(final String uuid, final String conferenceUuid, final int sandboxSlot, final int seatIndex,
                    final String userUuid, final Instant assignedAt,
                    final Instant createdAt, final Instant expiresAt) {
        this.uuid = uuid;
        this.conferenceUuid = conferenceUuid;
        this.sandboxSlot = sandboxSlot;
        this.seatIndex = seatIndex;
        this.userUuid = userUuid;
        this.assignedAt = assignedAt;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public String getUuid() { return uuid; }
    public String getConferenceUuid() { return conferenceUuid; }
    public int getSandboxSlot() { return sandboxSlot; }
    public int getSeatIndex() { return seatIndex; }
    public String getUserUuid() { return userUuid; }
    public Instant getAssignedAt() { return assignedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }

    /** Nombre determinístico del recurso K8s (Pod+Service) — no se persiste, se recalcula siempre. */
    public String podName() {
        return podName(conferenceUuid, sandboxSlot);
    }

    /**
     * Puerto del {@code ttyd} de ESTE asiento dentro del Pod compartido -- funcion pura de
     * {@code basePort + seatIndex}, valida tanto para el caso comun (seatIndex siempre 0, mismo
     * puerto de siempre) como para pods multi-asiento.
     */
    public int seatPort(final int basePort) {
        return basePort + seatIndex;
    }

    public static String podName(final String conferenceUuid, final int sandboxSlot) {
        return "sandbox-" + conferenceLabel(conferenceUuid) + "-" + sandboxSlot;
    }

    /**
     * Valor de la label {@code sandbox-conference} que comparten todos los Pods de un mismo
     * evento — permite que la NetworkPolicy de "internet habilitado" (Fase 3c,
     * SetSandboxInternetUseCase) seleccione todos los sandboxes del evento con un solo recurso,
     * sin importar cuantos slots tenga el pool.
     */
    public static String conferenceLabel(final String conferenceUuid) {
        final String compact = conferenceUuid.replace("-", "");
        return compact.substring(0, Math.min(8, compact.length()));
    }
}
