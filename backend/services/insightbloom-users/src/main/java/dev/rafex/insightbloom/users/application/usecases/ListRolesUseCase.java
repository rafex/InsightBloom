package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.Role;
import dev.rafex.insightbloom.users.domain.model.RoleScope;
import dev.rafex.insightbloom.users.domain.ports.RoleRepository;

import java.util.List;

public class ListRolesUseCase {
    private final RoleRepository roleRepository;

    public ListRolesUseCase(final RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    public List<Role> execute(final boolean activeOnly, final RoleScope scope) {
        if (scope != null) {
            final List<Role> byScope = roleRepository.findActiveByScope(scope);
            return activeOnly ? byScope : roleRepository.findAll().stream()
                    .filter(r -> r.getScope() == scope).toList();
        }
        return activeOnly ? roleRepository.findActive() : roleRepository.findAll();
    }
}
