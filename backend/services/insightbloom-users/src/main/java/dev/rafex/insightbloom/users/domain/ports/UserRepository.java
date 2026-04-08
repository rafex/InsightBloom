package dev.rafex.insightbloom.users.domain.ports;

import dev.rafex.insightbloom.users.domain.model.User;
import java.util.Optional;

public interface UserRepository {
    void save(User user);
    Optional<User> findByUuid(String uuid);
    Optional<User> findByUsername(String username);
}
