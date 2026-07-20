package dev.rafex.insightbloom.users.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Un dispositivo activo (o ya revocado) de un usuario en una herramienta puntual (Jitsi/IDE)
 * dentro de una conferencia -- ver DeviceAccessGuard, que usa estas filas tanto para contar
 * cuántos dispositivos tiene un usuario como para contar cuántas cuentas distintas comparte un
 * dispositivo.
 */
public class ToolDeviceSession {
    private final String uuid;
    private final String conferenceUuid;
    private final String userUuid;
    private final ToolKind tool;
    private final String deviceFingerprint;
    private final Instant firstSeenAt;
    private Instant lastSeenAt;
    private Instant revokedAt;

    public ToolDeviceSession(String conferenceUuid, String userUuid, ToolKind tool, String deviceFingerprint) {
        this.uuid = UUID.randomUUID().toString();
        this.conferenceUuid = conferenceUuid;
        this.userUuid = userUuid;
        this.tool = tool;
        this.deviceFingerprint = deviceFingerprint;
        this.firstSeenAt = Instant.now();
        this.lastSeenAt = this.firstSeenAt;
    }

    public ToolDeviceSession(String uuid, String conferenceUuid, String userUuid, ToolKind tool,
                              String deviceFingerprint, Instant firstSeenAt, Instant lastSeenAt, Instant revokedAt) {
        this.uuid = uuid;
        this.conferenceUuid = conferenceUuid;
        this.userUuid = userUuid;
        this.tool = tool;
        this.deviceFingerprint = deviceFingerprint;
        this.firstSeenAt = firstSeenAt;
        this.lastSeenAt = lastSeenAt;
        this.revokedAt = revokedAt;
    }

    public boolean isActive() {
        return revokedAt == null;
    }

    public String getUuid() { return uuid; }
    public String getConferenceUuid() { return conferenceUuid; }
    public String getUserUuid() { return userUuid; }
    public ToolKind getTool() { return tool; }
    public String getDeviceFingerprint() { return deviceFingerprint; }
    public Instant getFirstSeenAt() { return firstSeenAt; }
    public Instant getLastSeenAt() { return lastSeenAt; }
    public Instant getRevokedAt() { return revokedAt; }
}
