package dev.rafex.insightbloom.users.domain.model;

import java.time.Instant;
import java.util.UUID;

public class VenueSeat {
    private final String uuid;
    private final String conferenceUuid;
    private final String label;
    private final double x; // 0.0-1.0 relativo al ancho de la imagen del recinto
    private final double y; // 0.0-1.0 relativo al alto de la imagen del recinto
    private final Instant createdAt;

    public VenueSeat(final String conferenceUuid, final String label, final double x, final double y) {
        this.uuid = UUID.randomUUID().toString();
        this.conferenceUuid = conferenceUuid;
        this.label = label;
        this.x = x;
        this.y = y;
        this.createdAt = Instant.now();
    }

    public VenueSeat(final String uuid, final String conferenceUuid, final String label, final double x,
                      final double y, final Instant createdAt) {
        this.uuid = uuid;
        this.conferenceUuid = conferenceUuid;
        this.label = label;
        this.x = x;
        this.y = y;
        this.createdAt = createdAt;
    }

    public String getUuid() { return uuid; }
    public String getConferenceUuid() { return conferenceUuid; }
    public String getLabel() { return label; }
    public double getX() { return x; }
    public double getY() { return y; }
    public Instant getCreatedAt() { return createdAt; }
}
