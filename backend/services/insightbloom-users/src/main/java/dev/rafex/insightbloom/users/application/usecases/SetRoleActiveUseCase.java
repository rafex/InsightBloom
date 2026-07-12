package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.Role;
import dev.rafex.insightbloom.users.domain.ports.RoleRepository;

public class SetRoleActiveUseCase {
    private final RoleRepository roleRepository;

    public SetRoleActiveUseCase(final RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    public Role execute(final String uuid, final boolean active) {
        final Role role = roleRepository.findByUuid(uuid)
                .orElseThrow(() -> new IllegalArgumentException("role_not_found"));
        role.setActive(active);
        roleRepository.save(role);
        return role;
    }
}
