package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.Permission;
import dev.rafex.insightbloom.users.domain.model.Role;
import dev.rafex.insightbloom.users.domain.model.RoleScope;
import dev.rafex.insightbloom.users.domain.ports.RoleRepository;

import java.util.Set;

public class CreateRoleUseCase {
    private final RoleRepository roleRepository;

    public CreateRoleUseCase(final RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    public Role execute(final String key, final String name, final String description,
                         final RoleScope scope, final Set<Permission> permissions) {
        if (key == null || key.isBlank()) throw new IllegalArgumentException("key_required");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name_required");
        if (scope == null) throw new IllegalArgumentException("scope_required");
        if (roleRepository.existsByKey(key)) throw new IllegalArgumentException("key_already_exists");

        final Role role = new Role(key, name, description, scope, permissions != null ? permissions : Set.of());
        roleRepository.save(role);
        return role;
    }
}
