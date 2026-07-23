package dev.rafex.insightbloom.users.domain.services;

import dev.rafex.insightbloom.users.domain.model.EventRole;
import dev.rafex.insightbloom.users.domain.model.Permission;
import dev.rafex.insightbloom.users.domain.model.Role;
import dev.rafex.insightbloom.users.domain.model.RoleScope;
import dev.rafex.insightbloom.users.domain.ports.EventRoleRepository;
import dev.rafex.insightbloom.users.domain.ports.RoleRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class EventPermissionGuardTest {

    @Test
    void systemAdmin_bypassesWithoutEventRoleRow() {
        final EventRoleRepository eventRoleRepo = Mockito.mock(EventRoleRepository.class);
        final RoleRepository roleRepo = Mockito.mock(RoleRepository.class);
        final var guard = new EventPermissionGuard(eventRoleRepo, roleRepo);

        final boolean result = guard.hasPermission("event-1", "user-admin", "admin,organizer", Permission.ASSIGN_EVENT_ROLES);

        assertTrue(result);
        Mockito.verify(eventRoleRepo, Mockito.never()).findByEventAndUser(Mockito.any(), Mockito.any());
    }

    @Test
    void roleNameContainingAdmin_isNotTreatedAsSystemAdmin() {
        final EventRoleRepository eventRoleRepo = Mockito.mock(EventRoleRepository.class);
        final RoleRepository roleRepo = Mockito.mock(RoleRepository.class);
        Mockito.when(eventRoleRepo.findByEventAndUser("event-1", "user-1")).thenReturn(Optional.empty());

        final var guard = new EventPermissionGuard(eventRoleRepo, roleRepo);

        assertFalse(guard.hasPermission("event-1", "user-1", "notadmin", Permission.ASSIGN_EVENT_ROLES));
    }

    @Test
    void userWithMatchingRoleAssignment_hasPermission() {
        final EventRoleRepository eventRoleRepo = Mockito.mock(EventRoleRepository.class);
        final RoleRepository roleRepo = Mockito.mock(RoleRepository.class);
        final EventRole assignment = new EventRole("event-1", "user-1", "moderator");
        final Role moderatorRole = new Role("moderator", "Moderador", "desc", RoleScope.EVENT,
                Set.of(Permission.MODERATE_CONTENT, Permission.VIDEO_MODERATE));
        Mockito.when(eventRoleRepo.findByEventAndUser("event-1", "user-1")).thenReturn(Optional.of(assignment));
        Mockito.when(roleRepo.findByKey("moderator")).thenReturn(Optional.of(moderatorRole));

        final var guard = new EventPermissionGuard(eventRoleRepo, roleRepo);

        assertTrue(guard.hasPermission("event-1", "user-1", "attendee", Permission.MODERATE_CONTENT));
        assertFalse(guard.hasPermission("event-1", "user-1", "attendee", Permission.ASSIGN_EVENT_ROLES));
    }

    @Test
    void userWithoutAssignment_hasNoPermission() {
        final EventRoleRepository eventRoleRepo = Mockito.mock(EventRoleRepository.class);
        final RoleRepository roleRepo = Mockito.mock(RoleRepository.class);
        Mockito.when(eventRoleRepo.findByEventAndUser("event-1", "user-2")).thenReturn(Optional.empty());

        final var guard = new EventPermissionGuard(eventRoleRepo, roleRepo);

        assertFalse(guard.hasPermission("event-1", "user-2", "attendee", Permission.CHECK_IN));
    }

    @Test
    void inactiveRole_hasNoPermission() {
        final EventRoleRepository eventRoleRepo = Mockito.mock(EventRoleRepository.class);
        final RoleRepository roleRepo = Mockito.mock(RoleRepository.class);
        final EventRole assignment = new EventRole("event-1", "user-1", "checkin_staff");
        final Role inactiveRole = new Role("uuid-1", "checkin_staff", "Staff", "desc", RoleScope.EVENT,
                Set.of(Permission.CHECK_IN), false, java.time.Instant.now(), java.time.Instant.now());
        Mockito.when(eventRoleRepo.findByEventAndUser("event-1", "user-1")).thenReturn(Optional.of(assignment));
        Mockito.when(roleRepo.findByKey("checkin_staff")).thenReturn(Optional.of(inactiveRole));

        final var guard = new EventPermissionGuard(eventRoleRepo, roleRepo);

        assertFalse(guard.hasPermission("event-1", "user-1", "attendee", Permission.CHECK_IN));
    }
}
