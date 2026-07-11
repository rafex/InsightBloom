package dev.rafex.insightbloom.users.domain.ports;

import dev.rafex.insightbloom.users.domain.model.EventType;

import java.util.List;
import java.util.Optional;

public interface EventTypeRepository {
    void save(EventType eventType);

    Optional<EventType> findByUuid(String uuid);

    Optional<EventType> findByKey(String key);

    boolean existsByKey(String key);

    List<EventType> findAll();

    List<EventType> findActive();
}
