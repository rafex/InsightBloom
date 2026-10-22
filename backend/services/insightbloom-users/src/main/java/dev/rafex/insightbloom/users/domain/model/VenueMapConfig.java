package dev.rafex.insightbloom.users.domain.model;

/** Persisted renderer choice and venue geometry, independent from seat reservations. */
public record VenueMapConfig(String conferenceUuid, String engine, String layoutJson) {
    public VenueMapConfig {
        engine = "SEATMAP_CANVAS".equals(engine) ? "SEATMAP_CANVAS" : "FREEFORM";
        layoutJson = layoutJson == null || layoutJson.isBlank() ? "{}" : layoutJson;
    }
}
