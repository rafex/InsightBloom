package dev.rafex.insightbloom.users.domain.model;

import java.time.Instant;
import java.util.UUID;

public class Conference {
    private final String uuid;
    private final String friendlyId;
    private String name;
    private final String createdByUserUuid;
    private ConferenceStatus status;
    private final Instant createdAt;
    private Instant updatedAt;
    private Instant expiresAt; // nullable
    private Double latitude;
    private Double longitude;

    public Conference(String friendlyId, String name, String createdByUserUuid) {
        this.uuid = UUID.randomUUID().toString();
        this.friendlyId = friendlyId;
        this.name = name;
        this.createdByUserUuid = createdByUserUuid;
        this.status = ConferenceStatus.ACTIVE;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public Conference(String friendlyId, String name, String createdByUserUuid, Double latitude, Double longitude) {
        this(friendlyId, name, createdByUserUuid);
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public Conference(String friendlyId, String name, String createdByUserUuid, Instant expiresAt) {
        this(friendlyId, name, createdByUserUuid);
        this.expiresAt = expiresAt;
    }

    public Conference(String friendlyId, String name, String createdByUserUuid, Instant expiresAt, Double latitude, Double longitude) {
        this(friendlyId, name, createdByUserUuid);
        this.expiresAt = expiresAt;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public Conference(String uuid, String friendlyId, String name, String createdByUserUuid,
                      ConferenceStatus status, Instant createdAt, Instant updatedAt,
                      Instant expiresAt, Double latitude, Double longitude) {
        this.uuid = uuid;
        this.friendlyId = friendlyId;
        this.name = name;
        this.createdByUserUuid = createdByUserUuid;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.expiresAt = expiresAt;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public String getUuid() { return uuid; }
    public String getFriendlyId() { return friendlyId; }
    public String getName() { return name; }
    public String getCreatedByUserUuid() { return createdByUserUuid; }
    public ConferenceStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }
}
