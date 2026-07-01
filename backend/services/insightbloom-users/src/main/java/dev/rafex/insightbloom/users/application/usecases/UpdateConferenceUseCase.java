package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.Conference;
import dev.rafex.insightbloom.users.domain.ports.ConferenceRepository;

import java.util.Optional;

public class UpdateConferenceUseCase {
    private final ConferenceRepository conferenceRepository;

    public UpdateConferenceUseCase(final ConferenceRepository conferenceRepository) {
        this.conferenceRepository = conferenceRepository;
    }

    public record UpdateRequest(String displayName, String venue, String eventDate, String startTime,
                                 String endTime, Double latitude, Double longitude) {}

    /** Actualiza todo excepto friendlyId y uuid. Solo el creador puede editar. */
    public Optional<Conference> execute(final String uuid, final String requestingUserUuid,
                                         final UpdateRequest request) {
        return conferenceRepository.findByUuid(uuid)
                .filter(c -> c.getCreatedByUserUuid().equals(requestingUserUuid))
                .map(c -> {
                    if (request.displayName() != null && !request.displayName().isBlank()) {
                        c.setName(request.displayName());
                    }
                    c.setVenue(blankToNull(request.venue()));
                    c.setEventDate(blankToNull(request.eventDate()));
                    c.setStartTime(blankToNull(request.startTime()));
                    c.setEndTime(blankToNull(request.endTime()));
                    c.setLatitude(request.latitude());
                    c.setLongitude(request.longitude());
                    conferenceRepository.save(c);
                    return c;
                });
    }

    private static String blankToNull(final String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
