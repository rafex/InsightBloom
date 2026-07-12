package dev.rafex.insightbloom.users.domain.ports;

import dev.rafex.insightbloom.users.domain.model.Role;
import dev.rafex.insightbloom.users.domain.model.RoleScope;

import java.util.List;
import java.util.Optional;

public interface RoleRepository {
    void save(Role role);

    Optional<Role> findByUuid(String uuid);

    Optional<Role> findByKey(String key);

    boolean existsByKey(String key);

    List<Role> findAll();

    List<Role> findActive();

    List<Role> findActiveByScope(RoleScope scope);
}
