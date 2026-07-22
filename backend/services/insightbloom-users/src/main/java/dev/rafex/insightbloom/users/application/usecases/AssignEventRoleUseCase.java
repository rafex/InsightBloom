package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.EventRole;
import dev.rafex.insightbloom.users.domain.model.Permission;
import dev.rafex.insightbloom.users.domain.model.RoleScope;
import dev.rafex.insightbloom.users.domain.model.User;
import dev.rafex.insightbloom.users.domain.ports.EventRoleRepository;
import dev.rafex.insightbloom.users.domain.ports.RoleRepository;
import dev.rafex.insightbloom.users.domain.ports.UserRepository;
import dev.rafex.insightbloom.users.domain.services.EventPermissionGuard;

/** Asigna un rol de alcance EVENT a un usuario para un evento especifico (FR-005). */
public class AssignEventRoleUseCase {
    private final EventRoleRepository eventRoleRepository;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final EventPermissionGuard eventPermissionGuard;
    private final TicketUseCase ticketUseCase;

    public AssignEventRoleUseCase(final EventRoleRepository eventRoleRepository, final RoleRepository roleRepository,
                                   final UserRepository userRepository, final EventPermissionGuard eventPermissionGuard) {
        this(eventRoleRepository, roleRepository, userRepository, eventPermissionGuard, null);
    }

    public AssignEventRoleUseCase(final EventRoleRepository eventRoleRepository, final RoleRepository roleRepository,
                                   final UserRepository userRepository, final EventPermissionGuard eventPermissionGuard,
                                   final TicketUseCase ticketUseCase) {
        this.eventRoleRepository = eventRoleRepository;
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.eventPermissionGuard = eventPermissionGuard;
        this.ticketUseCase = ticketUseCase;
    }

    public EventRole execute(final String eventUuid, final String requestingUserUuid,
                              final String requestingUserLegacyRole, final String targetIdentifier,
                              final String roleKey) {
        if (!eventPermissionGuard.hasPermission(eventUuid, requestingUserUuid, requestingUserLegacyRole,
                Permission.ASSIGN_EVENT_ROLES)) {
            throw new SecurityException("forbidden");
        }
        final User targetUser = userRepository.findByEmail(targetIdentifier)
                .or(() -> userRepository.findByUsername(targetIdentifier))
                .orElseThrow(() -> new IllegalArgumentException("user_not_found"));
        final var role = roleRepository.findByKey(roleKey)
                .orElseThrow(() -> new IllegalArgumentException("role_not_found"));
        if (role.getScope() != RoleScope.EVENT) throw new IllegalArgumentException("role_not_event_scoped");
        if (!role.isActive()) throw new IllegalArgumentException("role_inactive");

        if (ticketUseCase != null && ("moderator".equalsIgnoreCase(roleKey)
                || role.hasPermission(Permission.MODERATE_CONTENT))) {
            // La reserva ocurre antes de guardar el rol: si el aforo está lleno, el usuario no
            // queda con un rol operativo que no podría usar.
            ticketUseCase.issueOperational(eventUuid, targetUser.getUuid());
        }
        final EventRole assignment = new EventRole(eventUuid, targetUser.getUuid(), roleKey);
        eventRoleRepository.save(assignment);
        return assignment;
    }
}
