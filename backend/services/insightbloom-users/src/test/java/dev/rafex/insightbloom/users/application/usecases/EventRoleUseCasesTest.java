package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.Conference;
import dev.rafex.insightbloom.users.domain.model.EventRole;
import dev.rafex.insightbloom.users.domain.model.Role;
import dev.rafex.insightbloom.users.domain.model.RoleScope;
import dev.rafex.insightbloom.users.domain.model.User;
import dev.rafex.insightbloom.users.domain.model.UserRole;
import dev.rafex.insightbloom.users.domain.ports.ConferenceRepository;
import dev.rafex.insightbloom.users.domain.ports.EventRoleRepository;
import dev.rafex.insightbloom.users.domain.ports.RoleRepository;
import dev.rafex.insightbloom.users.domain.ports.UserRepository;
import dev.rafex.insightbloom.users.domain.services.EventPermissionGuard;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class EventRoleUseCasesTest {

    @Test
    void assign_hostAssignsModerator_succeeds() {
        final EventRoleRepository eventRoleRepo = Mockito.mock(EventRoleRepository.class);
        final RoleRepository roleRepo = Mockito.mock(RoleRepository.class);
        final UserRepository userRepo = Mockito.mock(UserRepository.class);
        final EventPermissionGuard guard = Mockito.mock(EventPermissionGuard.class);

        final Role moderatorRole = new Role("moderator", "Moderador", "desc", RoleScope.EVENT, Set.of());
        final User target = new User("target-uuid", "jane", "Jane", "jane@test.com", UserRole.ATTENDEE);

        Mockito.when(guard.hasPermission(Mockito.eq("event-1"), Mockito.eq("host-uuid"), Mockito.any(),
                Mockito.eq(dev.rafex.insightbloom.users.domain.model.Permission.ASSIGN_EVENT_ROLES))).thenReturn(true);
        Mockito.when(userRepo.findByEmail("jane@test.com")).thenReturn(Optional.of(target));
        Mockito.when(roleRepo.findByKey("moderator")).thenReturn(Optional.of(moderatorRole));

        final var useCase = new AssignEventRoleUseCase(eventRoleRepo, roleRepo, userRepo, guard);
        final EventRole result = useCase.execute("event-1", "host-uuid", "organizer", "jane@test.com", "moderator");

        assertEquals("target-uuid", result.getUserUuid());
        assertEquals("moderator", result.getRoleKey());
        Mockito.verify(eventRoleRepo).save(Mockito.any());
    }

    @Test
    void assign_withoutPermission_throwsSecurityException() {
        final EventRoleRepository eventRoleRepo = Mockito.mock(EventRoleRepository.class);
        final RoleRepository roleRepo = Mockito.mock(RoleRepository.class);
        final UserRepository userRepo = Mockito.mock(UserRepository.class);
        final EventPermissionGuard guard = Mockito.mock(EventPermissionGuard.class);
        Mockito.when(guard.hasPermission(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any())).thenReturn(false);

        final var useCase = new AssignEventRoleUseCase(eventRoleRepo, roleRepo, userRepo, guard);
        assertThrows(SecurityException.class,
                () -> useCase.execute("event-1", "attendee-uuid", "attendee", "jane@test.com", "moderator"));
        Mockito.verify(eventRoleRepo, Mockito.never()).save(Mockito.any());
    }

    @Test
    void assign_platformScopedRole_rejected() {
        final EventRoleRepository eventRoleRepo = Mockito.mock(EventRoleRepository.class);
        final RoleRepository roleRepo = Mockito.mock(RoleRepository.class);
        final UserRepository userRepo = Mockito.mock(UserRepository.class);
        final EventPermissionGuard guard = Mockito.mock(EventPermissionGuard.class);
        final Role platformRole = new Role("organizer", "Organizador", "desc", RoleScope.PLATFORM, Set.of());
        final User target = new User("target-uuid", "jane", "Jane", "jane@test.com", UserRole.ATTENDEE);

        Mockito.when(guard.hasPermission(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any())).thenReturn(true);
        Mockito.when(userRepo.findByEmail("jane@test.com")).thenReturn(Optional.of(target));
        Mockito.when(roleRepo.findByKey("organizer")).thenReturn(Optional.of(platformRole));

        final var useCase = new AssignEventRoleUseCase(eventRoleRepo, roleRepo, userRepo, guard);
        final var ex = assertThrows(IllegalArgumentException.class,
                () -> useCase.execute("event-1", "host-uuid", "organizer", "jane@test.com", "organizer"));
        assertEquals("role_not_event_scoped", ex.getMessage());
    }

    @Test
    void remove_originalCreator_blocked() {
        final EventRoleRepository eventRoleRepo = Mockito.mock(EventRoleRepository.class);
        final ConferenceRepository conferenceRepo = Mockito.mock(ConferenceRepository.class);
        final EventPermissionGuard guard = Mockito.mock(EventPermissionGuard.class);
        final Conference conference = new Conference("charla-2026", "Charla 2026", "creator-uuid");

        Mockito.when(guard.hasPermission(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any())).thenReturn(true);
        Mockito.when(conferenceRepo.findByUuid(conference.getUuid())).thenReturn(Optional.of(conference));

        final var useCase = new RemoveEventRoleUseCase(eventRoleRepo, conferenceRepo, guard);
        final var ex = assertThrows(IllegalStateException.class,
                () -> useCase.execute(conference.getUuid(), "creator-uuid", "organizer", "creator-uuid"));
        assertEquals("cannot_remove_original_host", ex.getMessage());
        Mockito.verify(eventRoleRepo, Mockito.never()).delete(Mockito.any(), Mockito.any());
    }

    @Test
    void remove_nonCreatorAssignment_succeeds() {
        final EventRoleRepository eventRoleRepo = Mockito.mock(EventRoleRepository.class);
        final ConferenceRepository conferenceRepo = Mockito.mock(ConferenceRepository.class);
        final EventPermissionGuard guard = Mockito.mock(EventPermissionGuard.class);
        final Conference conference = new Conference("charla-2026", "Charla 2026", "creator-uuid");

        Mockito.when(guard.hasPermission(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any())).thenReturn(true);
        Mockito.when(conferenceRepo.findByUuid(conference.getUuid())).thenReturn(Optional.of(conference));

        final var useCase = new RemoveEventRoleUseCase(eventRoleRepo, conferenceRepo, guard);
        useCase.execute(conference.getUuid(), "creator-uuid", "organizer", "moderator-uuid");

        Mockito.verify(eventRoleRepo).delete(conference.getUuid(), "moderator-uuid");
    }
}
