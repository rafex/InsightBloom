package dev.rafex.insightbloom.users.domain.ports;

import dev.rafex.insightbloom.users.domain.model.EventRole;

import java.util.List;
import java.util.Optional;

public interface EventRoleRepository {
    void save(EventRole eventRole);

    void delete(String eventUuid, String userUuid);

    Optional<EventRole> findByEventAndUser(String eventUuid, String userUuid);

    List<EventRole> findByEvent(String eventUuid);
}
