package dev.rafex.insightbloom.users.domain.ports;

import dev.rafex.insightbloom.users.domain.model.GuestUser;
import java.util.Optional;

public interface GuestUserRepository {
    void save(GuestUser guest);
    Optional<GuestUser> findByUuid(String uuid);
}
