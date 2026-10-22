package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.VenueMapConfig;
import dev.rafex.insightbloom.users.domain.ports.ConferenceRepository;
import dev.rafex.insightbloom.users.domain.ports.VenueMapConfigRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;

public class VenueMapConfigUseCase {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final ConferenceRepository conferences;
    private final VenueMapConfigRepository configs;

    public VenueMapConfigUseCase(final ConferenceRepository conferences, final VenueMapConfigRepository configs) {
        this.conferences = conferences; this.configs = configs;
    }

    public VenueMapConfig get(final String conferenceUuid) {
        return configs.findByConference(conferenceUuid).orElseGet(() -> new VenueMapConfig(conferenceUuid, "FREEFORM", "{}"));
    }

    public Optional<VenueMapConfig> set(final String conferenceUuid, final String userUuid, final String engine, final String layoutJson) {
        if (!"FREEFORM".equals(engine) && !"SEATMAP_CANVAS".equals(engine)) throw new IllegalArgumentException("invalid_venue_map_engine");
        if (layoutJson == null || layoutJson.length() > 1_000_000) throw new IllegalArgumentException("invalid_venue_map_layout");
        try {
            final JsonNode layout = JSON.readTree(layoutJson);
            if (layout == null || !layout.isObject()) throw new IllegalArgumentException("invalid_venue_map_layout");
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalArgumentException("invalid_venue_map_layout", e);
        }
        return conferences.findByUuid(conferenceUuid).filter(c -> c.getCreatedByUserUuid().equals(userUuid)).map(c -> {
            final VenueMapConfig config = new VenueMapConfig(conferenceUuid, engine, layoutJson);
            configs.save(config);
            return config;
        });
    }
}
