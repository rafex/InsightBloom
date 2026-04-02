package dev.rafex.insightbloom.users.domain.model;

import java.time.Instant;
import java.util.UUID;

public class GuestUser {
    private final String uuid;
    private String displayName;
    private String deviceFingerprint;
    private String conferenceUuid;
    private final Instant createdAt;

    public GuestUser(String displayName, String deviceFingerprint, String conferenceUuid) {
        this.uuid = UUID.randomUUID().toString();
        this.displayName = displayName;
        this.deviceFingerprint = deviceFingerprint;
        this.conferenceUuid = conferenceUuid;
        this.createdAt = Instant.now();
    }

    public GuestUser(String uuid, String displayName, String deviceFingerprint, String conferenceUuid, Instant createdAt) {
        this.uuid = uuid;
        this.displayName = displayName;
        this.deviceFingerprint = deviceFingerprint;
        this.conferenceUuid = conferenceUuid;
        this.createdAt = createdAt;
    }

    public String getUuid() { return uuid; }
    public String getDisplayName() { return displayName; }
    public String getDeviceFingerprint() { return deviceFingerprint; }
    public String getConferenceUuid() { return conferenceUuid; }
    public Instant getCreatedAt() { return createdAt; }
}
