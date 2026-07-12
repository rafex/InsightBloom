package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.Permission;
import dev.rafex.insightbloom.users.domain.ports.EventRoleRepository;
import dev.rafex.insightbloom.users.domain.ports.UserRepository;
import dev.rafex.insightbloom.users.domain.services.EventPermissionGuard;

import java.util.List;
import java.util.Optional;

/** Lista las asignaciones de rol de un evento, visible solo para quien tenga ASSIGN_EVENT_ROLES (FR-010). */
public class ListEventRolesUseCase {
    private final EventRoleRepository eventRoleRepository;
    private final UserRepository userRepository;
    private final EventPermissionGuard eventPermissionGuard;

    public ListEventRolesUseCase(final EventRoleRepository eventRoleRepository, final UserRepository userRepository,
                                  final EventPermissionGuard eventPermissionGuard) {
        this.eventRoleRepository = eventRoleRepository;
        this.userRepository = userRepository;
        this.eventPermissionGuard = eventPermissionGuard;
    }

    public record EventRoleView(String userUuid, String displayName, String email, String roleKey, String assignedAt) {}

    public Optional<List<EventRoleView>> execute(final String eventUuid, final String requestingUserUuid,
                                                  final String requestingUserLegacyRole) {
        if (!eventPermissionGuard.hasPermission(eventUuid, requestingUserUuid, requestingUserLegacyRole,
                Permission.ASSIGN_EVENT_ROLES)) {
            return Optional.empty();
        }
        final List<EventRoleView> views = eventRoleRepository.findByEvent(eventUuid).stream()
                .map(assignment -> {
                    final var user = userRepository.findByUuid(assignment.getUserUuid()).orElse(null);
                    return new EventRoleView(
                            assignment.getUserUuid(),
                            user != null ? user.getDisplayName() : null,
                            user != null ? user.getEmail() : null,
                            assignment.getRoleKey(),
                            assignment.getAssignedAt().toString());
                })
                .toList();
        return Optional.of(views);
    }
}
