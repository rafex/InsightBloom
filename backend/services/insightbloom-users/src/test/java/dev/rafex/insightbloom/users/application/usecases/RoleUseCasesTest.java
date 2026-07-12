package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.Permission;
import dev.rafex.insightbloom.users.domain.model.Role;
import dev.rafex.insightbloom.users.domain.model.RoleScope;
import dev.rafex.insightbloom.users.domain.ports.RoleRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RoleUseCasesTest {

    @Test
    void create_duplicateKey_throwsClearError() {
        final RoleRepository repo = Mockito.mock(RoleRepository.class);
        Mockito.when(repo.existsByKey("host")).thenReturn(true);

        final var useCase = new CreateRoleUseCase(repo);
        final var ex = assertThrows(IllegalArgumentException.class,
                () -> useCase.execute("host", "Host", "desc", RoleScope.EVENT, Set.of(Permission.MODERATE_CONTENT)));
        assertEquals("key_already_exists", ex.getMessage());
        Mockito.verify(repo, Mockito.never()).save(Mockito.any());
    }

    @Test
    void create_validRole_savesRole() {
        final RoleRepository repo = Mockito.mock(RoleRepository.class);
        Mockito.when(repo.existsByKey("staff_coordinator")).thenReturn(false);

        final Role created = new CreateRoleUseCase(repo).execute(
                "staff_coordinator", "Coordinador de Staff", "desc", RoleScope.EVENT,
                Set.of(Permission.CHECK_IN, Permission.MODERATE_CONTENT));

        assertEquals("staff_coordinator", created.getKey());
        assertTrue(created.isActive());
        assertEquals(RoleScope.EVENT, created.getScope());
        Mockito.verify(repo).save(created);
    }

    @Test
    void update_replacesPermissionsCompletely() {
        final RoleRepository repo = Mockito.mock(RoleRepository.class);
        final Role existing = new Role("moderator", "Moderador", "desc", RoleScope.EVENT,
                Set.of(Permission.MODERATE_CONTENT));
        Mockito.when(repo.findByUuid(existing.getUuid())).thenReturn(Optional.of(existing));

        final Role updated = new UpdateRoleUseCase(repo)
                .execute(existing.getUuid(), "Moderador Senior", "nueva desc", Set.of(Permission.VIDEO_MODERATE));

        assertEquals(Set.of(Permission.VIDEO_MODERATE), updated.getPermissions());
        assertEquals("Moderador Senior", updated.getName());
        Mockito.verify(repo).save(existing);
    }

    @Test
    void setActive_deactivate_doesNotDelete() {
        final RoleRepository repo = Mockito.mock(RoleRepository.class);
        final Role existing = new Role("guest_presenter", "Presentador invitado", "desc", RoleScope.EVENT,
                Set.of(Permission.MANAGE_PRESENTATION));
        Mockito.when(repo.findByUuid(existing.getUuid())).thenReturn(Optional.of(existing));

        final Role result = new SetRoleActiveUseCase(repo).execute(existing.getUuid(), false);

        assertFalse(result.isActive());
        Mockito.verify(repo).save(existing);
    }

    @Test
    void list_byScope_filtersOtherScope() {
        final RoleRepository repo = Mockito.mock(RoleRepository.class);
        final Role eventRole = new Role("host", "Host", "desc", RoleScope.EVENT, Set.of());
        Mockito.when(repo.findActiveByScope(RoleScope.EVENT)).thenReturn(List.of(eventRole));

        final List<Role> result = new ListRolesUseCase(repo).execute(true, RoleScope.EVENT);

        assertEquals(1, result.size());
        Mockito.verify(repo, Mockito.never()).findActive();
    }
}
