package dev.rafex.insightbloom.users.domain.ports;

import dev.rafex.insightbloom.users.domain.model.User;
import dev.rafex.insightbloom.users.domain.model.UserRole;
import dev.rafex.insightbloom.users.domain.model.UserStatus;
import java.util.List;
import java.util.Optional;

public interface UserRepository {
    void save(User user);
    Optional<User> findByUuid(String uuid);
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    Optional<User> findByPhone(String phone);

    /**
     * @param status filtro exacto, null = todos los status
     * @param role filtro por rol (un usuario puede tener varios), null = todos los roles
     * @param sort "username" = orden alfabético ascendente, cualquier otro valor (incluido null) = created_at DESC
     */
    List<User> findAll(int page, int pageSize, UserStatus status, UserRole role, String sort);
    long countAll(UserStatus status, UserRole role);
}
