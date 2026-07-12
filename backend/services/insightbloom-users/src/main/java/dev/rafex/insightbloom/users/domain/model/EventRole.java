package dev.rafex.insightbloom.users.domain.model;

import java.time.Instant;
import java.util.UUID;

/** Asignación de un rol de alcance EVENT a un usuario, para un evento específico (DEC-0021). */
public class EventRole {
    private final String uuid;
    private final String eventUuid;
    private final String userUuid;
    private final String roleKey;
    private final Instant assignedAt;

    public EventRole(final String eventUuid, final String userUuid, final String roleKey) {
        this.uuid = UUID.randomUUID().toString();
        this.eventUuid = eventUuid;
        this.userUuid = userUuid;
        this.roleKey = roleKey;
        this.assignedAt = Instant.now();
    }

    public EventRole(final String uuid, final String eventUuid, final String userUuid, final String roleKey,
                      final Instant assignedAt) {
        this.uuid = uuid;
        this.eventUuid = eventUuid;
        this.userUuid = userUuid;
        this.roleKey = roleKey;
        this.assignedAt = assignedAt;
    }

    public String getUuid() { return uuid; }
    public String getEventUuid() { return eventUuid; }
    public String getUserUuid() { return userUuid; }
    public String getRoleKey() { return roleKey; }
    public Instant getAssignedAt() { return assignedAt; }
}
