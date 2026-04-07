package dev.rafex.insightbloom.users.domain.ports;

import dev.rafex.insightbloom.users.domain.model.Conference;
import java.util.List;
import java.util.Optional;

public interface ConferenceRepository {
    void save(Conference conference);
    Optional<Conference> findByUuid(String uuid);
    Optional<Conference> findByFriendlyId(String friendlyId);
    boolean existsByFriendlyId(String friendlyId);
    List<Conference> findByUser(String userUuid);
}
