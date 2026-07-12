package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.Permission;
import dev.rafex.insightbloom.users.domain.model.Role;
import dev.rafex.insightbloom.users.domain.ports.RoleRepository;

import java.util.Set;

public class UpdateRoleUseCase {
    private final RoleRepository roleRepository;

    public UpdateRoleUseCase(final RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    public Role execute(final String uuid, final String name, final String description,
                         final Set<Permission> permissions) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name_required");
        final Role role = roleRepository.findByUuid(uuid)
                .orElseThrow(() -> new IllegalArgumentException("role_not_found"));
        role.update(name, description, permissions != null ? permissions : Set.of());
        roleRepository.save(role);
        return role;
    }
}
