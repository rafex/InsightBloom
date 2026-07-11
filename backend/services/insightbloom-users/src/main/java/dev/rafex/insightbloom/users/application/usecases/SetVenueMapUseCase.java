package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.Conference;
import dev.rafex.insightbloom.users.domain.ports.ConferenceRepository;

import java.util.Optional;

/** Guarda la imagen del recinto (data URL) usada como fondo del editor de asientos. */
public class SetVenueMapUseCase {
    private final ConferenceRepository conferenceRepository;

    public SetVenueMapUseCase(final ConferenceRepository conferenceRepository) {
        this.conferenceRepository = conferenceRepository;
    }

    public Optional<Conference> execute(final String conferenceUuid, final String requestingUserUuid,
                                         final String imageBase64) {
        return conferenceRepository.findByUuid(conferenceUuid)
                .filter(c -> c.getCreatedByUserUuid().equals(requestingUserUuid))
                .map(c -> {
                    c.setVenueMapBase64(imageBase64);
                    conferenceRepository.save(c);
                    return c;
                });
    }
}
