package dev.rafex.insightbloom.users.domain.model;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** Tipo de evento administrado por ADMIN: nombre, slug único, descripción y capacidades activas. */
public class EventType {
    private final String uuid;
    private final String key;
    private String name;
    private String description;
    private Set<EventCapability> capabilities;
    private boolean active;
    private final Instant createdAt;
    private Instant updatedAt;

    public EventType(final String key, final String name, final String description,
                      final Set<EventCapability> capabilities) {
        this.uuid = UUID.randomUUID().toString();
        this.key = key;
        this.name = name;
        this.description = description;
        this.capabilities = capabilities;
        this.active = true;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public EventType(final String uuid, final String key, final String name, final String description,
                      final Set<EventCapability> capabilities, final boolean active,
                      final Instant createdAt, final Instant updatedAt) {
        this.uuid = uuid;
        this.key = key;
        this.name = name;
        this.description = description;
        this.capabilities = capabilities;
        this.active = active;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getUuid() { return uuid; }
    public String getKey() { return key; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public Set<EventCapability> getCapabilities() { return capabilities; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public boolean hasCapability(final EventCapability capability) {
        return capabilities.contains(capability);
    }

    public void update(final String name, final String description, final Set<EventCapability> capabilities) {
        this.name = name;
        this.description = description;
        this.capabilities = capabilities;
        this.updatedAt = Instant.now();
    }

    public void setActive(final boolean active) {
        this.active = active;
        this.updatedAt = Instant.now();
    }
}
