package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.Permission;
import dev.rafex.insightbloom.users.domain.ports.ConferenceRepository;
import dev.rafex.insightbloom.users.domain.ports.EventRoleRepository;
import dev.rafex.insightbloom.users.domain.services.EventPermissionGuard;

/** Quita la asignacion de rol de un usuario para un evento (FR-006). */
public class RemoveEventRoleUseCase {
    private final EventRoleRepository eventRoleRepository;
    private final ConferenceRepository conferenceRepository;
    private final EventPermissionGuard eventPermissionGuard;

    public RemoveEventRoleUseCase(final EventRoleRepository eventRoleRepository,
                                   final ConferenceRepository conferenceRepository,
                                   final EventPermissionGuard eventPermissionGuard) {
        this.eventRoleRepository = eventRoleRepository;
        this.conferenceRepository = conferenceRepository;
        this.eventPermissionGuard = eventPermissionGuard;
    }

    public void execute(final String eventUuid, final String requestingUserUuid,
                         final String requestingUserLegacyRole, final String targetUserUuid) {
        if (!eventPermissionGuard.hasPermission(eventUuid, requestingUserUuid, requestingUserLegacyRole,
                Permission.ASSIGN_EVENT_ROLES)) {
            throw new SecurityException("forbidden");
        }
        final boolean isOriginalCreator = conferenceRepository.findByUuid(eventUuid)
                .map(c -> c.getCreatedByUserUuid().equals(targetUserUuid))
                .orElse(false);
        if (isOriginalCreator) throw new IllegalStateException("cannot_remove_original_host");
        eventRoleRepository.delete(eventUuid, targetUserUuid);
    }
}
