package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.VenueMapConfig;
import dev.rafex.insightbloom.users.domain.ports.ConferenceRepository;
import dev.rafex.insightbloom.users.domain.ports.VenueMapConfigRepository;
import dev.rafex.insightbloom.users.domain.ports.VenueSeatRepository;
import java.util.Optional;

/** Copies only the venue design; target seats receive new identities and no reservations. */
public class CloneVenueMapUseCase {
    private final ConferenceRepository conferences;
    private final VenueMapConfigRepository configs;
    private final VenueSeatRepository seats;
    private final DefineVenueSeatsUseCase defineSeats;

    public CloneVenueMapUseCase(final ConferenceRepository conferences, final VenueMapConfigRepository configs,
                                final VenueSeatRepository seats, final DefineVenueSeatsUseCase defineSeats) {
        this.conferences = conferences; this.configs = configs; this.seats = seats; this.defineSeats = defineSeats;
    }

    public Optional<VenueMapConfig> execute(final String targetUuid, final String sourceUuid, final String userUuid) {
        final var target = conferences.findByUuid(targetUuid).filter(c -> c.getCreatedByUserUuid().equals(userUuid));
        final var source = conferences.findByUuid(sourceUuid).filter(c -> c.getCreatedByUserUuid().equals(userUuid));
        if (target.isEmpty() || source.isEmpty()) return Optional.empty();
        final var sourceSeats = seats.findByConference(sourceUuid).stream()
                .map(s -> new DefineVenueSeatsUseCase.SeatInput(null, s.getLabel(), s.getX(), s.getY())).toList();
        final var copied = defineSeats.execute(targetUuid, userUuid, sourceSeats);
        if (copied.isEmpty()) return Optional.empty();
        final VenueMapConfig sourceConfig = configs.findByConference(sourceUuid)
                .orElseGet(() -> new VenueMapConfig(sourceUuid, "FREEFORM", "{}"));
        final VenueMapConfig result = new VenueMapConfig(targetUuid, sourceConfig.engine(), sourceConfig.layoutJson());
        configs.save(result);
        return Optional.of(result);
    }
}
