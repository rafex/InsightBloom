package dev.rafex.insightbloom.users.domain.services;

import dev.rafex.insightbloom.users.domain.model.Permission;
import dev.rafex.insightbloom.users.domain.ports.EventRoleRepository;
import dev.rafex.insightbloom.users.domain.ports.RoleRepository;

/**
 * Resuelve si un usuario tiene un permiso de alcance EVENT sobre un evento especifico (DEC-0021).
 * Un usuario con el rol de plataforma `system_admin` tiene bypass total (FR-008) sin necesidad de
 * una fila en `event_roles`. Para el resto, se busca su asignacion en `event_roles` para ese
 * evento y se resuelven los permisos del `Role` correspondiente.
 */
public class EventPermissionGuard {
    private static final String SYSTEM_ADMIN_ROLE_KEY = "system_admin";

    private final EventRoleRepository eventRoleRepository;
    private final RoleRepository roleRepository;

    public EventPermissionGuard(final EventRoleRepository eventRoleRepository,
                                 final RoleRepository roleRepository) {
        this.eventRoleRepository = eventRoleRepository;
        this.roleRepository = roleRepository;
    }

    /**
     * @param userLegacyRole el CSV de {@code UserRole} del usuario (ej. "admin,organizer"), usado
     *                       solo para detectar el bypass de administrador de sistema (FR-008).
     */
    public boolean hasPermission(final String eventUuid, final String userUuid, final String userLegacyRole,
                                  final Permission permission) {
        if (isSystemAdmin(userLegacyRole)) return true;
        return eventRoleRepository.findByEventAndUser(eventUuid, userUuid)
                .flatMap(assignment -> roleRepository.findByKey(assignment.getRoleKey()))
                .map(role -> role.isActive() && role.hasPermission(permission))
                .orElse(false);
    }

    private boolean isSystemAdmin(final String userLegacyRole) {
        // Bypass ligado al rol legado "admin" (UserRole.ADMIN, DEC-0011): todo admin de
        // plataforma actua como system_admin. Cuando exista asignacion explicita de roles de
        // plataforma (fuera de alcance de esta iteracion), este chequeo se resuelve por key.
        return userLegacyRole != null && userLegacyRole.contains("admin");
    }
}
