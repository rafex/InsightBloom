package dev.rafex.insightbloom.users.domain.model;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** Rol administrado por un usuario con `MANAGE_USERS`: nombre, slug único, alcance y permisos activos. */
public class Role {
    private final String uuid;
    private final String key;
    private String name;
    private String description;
    private final RoleScope scope;
    private Set<Permission> permissions;
    private boolean active;
    private final Instant createdAt;
    private Instant updatedAt;

    public Role(final String key, final String name, final String description, final RoleScope scope,
                final Set<Permission> permissions) {
        this.uuid = UUID.randomUUID().toString();
        this.key = key;
        this.name = name;
        this.description = description;
        this.scope = scope;
        this.permissions = permissions;
        this.active = true;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public Role(final String uuid, final String key, final String name, final String description,
                final RoleScope scope, final Set<Permission> permissions, final boolean active,
                final Instant createdAt, final Instant updatedAt) {
        this.uuid = uuid;
        this.key = key;
        this.name = name;
        this.description = description;
        this.scope = scope;
        this.permissions = permissions;
        this.active = active;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getUuid() { return uuid; }
    public String getKey() { return key; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public RoleScope getScope() { return scope; }
    public Set<Permission> getPermissions() { return permissions; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public boolean hasPermission(final Permission permission) {
        return permissions.contains(permission);
    }

    public void update(final String name, final String description, final Set<Permission> permissions) {
        this.name = name;
        this.description = description;
        this.permissions = permissions;
        this.updatedAt = Instant.now();
    }

    public void setActive(final boolean active) {
        this.active = active;
        this.updatedAt = Instant.now();
    }
}
