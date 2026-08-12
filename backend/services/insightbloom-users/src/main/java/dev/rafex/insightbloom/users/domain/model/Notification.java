package dev.rafex.insightbloom.users.domain.model;

import java.time.Instant;

/** Notificación dentro del portal para un usuario puntual (campana del header). */
public final class Notification {
    private final String uuid;
    private final String userUuid;
    private final String type;
    private final String title;
    private final String body;
    private final String linkUrl;
    private final Instant createdAt;
    private final Instant readAt;

    public Notification(final String uuid, final String userUuid, final String type, final String title,
                         final String body, final String linkUrl, final Instant createdAt, final Instant readAt) {
        this.uuid = uuid;
        this.userUuid = userUuid;
        this.type = type;
        this.title = title;
        this.body = body;
        this.linkUrl = linkUrl;
        this.createdAt = createdAt;
        this.readAt = readAt;
    }

    public String getUuid() { return uuid; }
    public String getUserUuid() { return userUuid; }
    public String getType() { return type; }
    public String getTitle() { return title; }
    public String getBody() { return body; }
    public String getLinkUrl() { return linkUrl; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getReadAt() { return readAt; }
}
