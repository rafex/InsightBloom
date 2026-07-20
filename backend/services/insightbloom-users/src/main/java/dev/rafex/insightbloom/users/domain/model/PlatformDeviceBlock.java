package dev.rafex.insightbloom.users.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Dispositivo bloqueado a nivel PLATAFORMA (no de un evento puntual) por
 * {@link PlatformDeviceBlockReason#MULTI_ACCOUNT} o {@link PlatformDeviceBlockReason#REGISTRATION_SPAM}
 * -- ver PlatformDeviceGuard. Queda visible en /dashboard/admin/device-access hasta que un
 * system_admin lo desbloquea.
 */
public class PlatformDeviceBlock {
    private final String uuid;
    private final String deviceFingerprint;
    private final PlatformDeviceBlockReason reason;
    private final int relatedCount;
    private final Instant blockedAt;
    private Instant unblockedAt;
    private String unblockedBy;

    public PlatformDeviceBlock(String deviceFingerprint, PlatformDeviceBlockReason reason, int relatedCount) {
        this.uuid = UUID.randomUUID().toString();
        this.deviceFingerprint = deviceFingerprint;
        this.reason = reason;
        this.relatedCount = relatedCount;
        this.blockedAt = Instant.now();
    }

    public PlatformDeviceBlock(String uuid, String deviceFingerprint, PlatformDeviceBlockReason reason,
                                int relatedCount, Instant blockedAt, Instant unblockedAt, String unblockedBy) {
        this.uuid = uuid;
        this.deviceFingerprint = deviceFingerprint;
        this.reason = reason;
        this.relatedCount = relatedCount;
        this.blockedAt = blockedAt;
        this.unblockedAt = unblockedAt;
        this.unblockedBy = unblockedBy;
    }

    public boolean isActive() {
        return unblockedAt == null;
    }

    public String getUuid() { return uuid; }
    public String getDeviceFingerprint() { return deviceFingerprint; }
    public PlatformDeviceBlockReason getReason() { return reason; }
    public int getRelatedCount() { return relatedCount; }
    public Instant getBlockedAt() { return blockedAt; }
    public Instant getUnblockedAt() { return unblockedAt; }
    public String getUnblockedBy() { return unblockedBy; }
}
