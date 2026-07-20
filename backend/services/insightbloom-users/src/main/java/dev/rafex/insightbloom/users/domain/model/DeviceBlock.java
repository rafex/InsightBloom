package dev.rafex.insightbloom.users.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Un dispositivo bloqueado dentro de una conferencia por superar {@code maxAccountsPerDevice}
 * (demasiadas cuentas distintas logueadas desde el mismo fingerprint) -- ver DeviceAccessGuard.
 * Queda visible en el dashboard ("Bloqueos") hasta que un moderador lo desbloquea.
 */
public class DeviceBlock {
    private final String uuid;
    private final String conferenceUuid;
    private final String deviceFingerprint;
    private final int accountCount;
    private final Instant blockedAt;
    private Instant unblockedAt;
    private String unblockedBy;

    public DeviceBlock(String conferenceUuid, String deviceFingerprint, int accountCount) {
        this.uuid = UUID.randomUUID().toString();
        this.conferenceUuid = conferenceUuid;
        this.deviceFingerprint = deviceFingerprint;
        this.accountCount = accountCount;
        this.blockedAt = Instant.now();
    }

    public DeviceBlock(String uuid, String conferenceUuid, String deviceFingerprint, int accountCount,
                        Instant blockedAt, Instant unblockedAt, String unblockedBy) {
        this.uuid = uuid;
        this.conferenceUuid = conferenceUuid;
        this.deviceFingerprint = deviceFingerprint;
        this.accountCount = accountCount;
        this.blockedAt = blockedAt;
        this.unblockedAt = unblockedAt;
        this.unblockedBy = unblockedBy;
    }

    public boolean isActive() {
        return unblockedAt == null;
    }

    public String getUuid() { return uuid; }
    public String getConferenceUuid() { return conferenceUuid; }
    public String getDeviceFingerprint() { return deviceFingerprint; }
    public int getAccountCount() { return accountCount; }
    public Instant getBlockedAt() { return blockedAt; }
    public Instant getUnblockedAt() { return unblockedAt; }
    public String getUnblockedBy() { return unblockedBy; }
}
