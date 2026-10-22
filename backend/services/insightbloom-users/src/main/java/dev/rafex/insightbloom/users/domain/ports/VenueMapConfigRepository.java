package dev.rafex.insightbloom.users.domain.ports;

import dev.rafex.insightbloom.users.domain.model.VenueMapConfig;
import java.util.Optional;

public interface VenueMapConfigRepository {
    Optional<VenueMapConfig> findByConference(String conferenceUuid);
    void save(VenueMapConfig config);
}
