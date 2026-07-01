package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.Conference;
import dev.rafex.insightbloom.users.domain.ports.ConferenceRepository;
import dev.rafex.insightbloom.users.domain.services.FriendlyIdService;

import java.time.Instant;

public class CreateConferenceUseCase {
    private final ConferenceRepository conferenceRepository;
    private final FriendlyIdService friendlyIdService;

    public CreateConferenceUseCase(ConferenceRepository conferenceRepository, FriendlyIdService friendlyIdService) {
        this.conferenceRepository = conferenceRepository;
        this.friendlyIdService = friendlyIdService;
    }

    public record CreateRequest(String name, String displayName, String createdByUserUuid, String expiresAt,
                                Double latitude, Double longitude,
                                String eventDate, String venue, String startTime, String endTime) {}
    public record CreateResult(String conferenceId, String friendlyId, String name, String status,
                               String expiresAt, Double latitude, Double longitude,
                               String eventDate, String venue, String startTime, String endTime) {}

    public CreateResult execute(CreateRequest request) {
        String friendlyId = friendlyIdService.generate(request.name());
        String displayName = blankToNull(request.displayName()) != null ? request.displayName() : request.name();
        Instant expiresAt = parseInstant(request.expiresAt());
        Conference conference = new Conference(friendlyId, displayName, request.createdByUserUuid(),
                expiresAt, request.latitude(), request.longitude());
        conference.setEventDate(blankToNull(request.eventDate()));
        conference.setVenue(blankToNull(request.venue()));
        conference.setStartTime(blankToNull(request.startTime()));
        conference.setEndTime(blankToNull(request.endTime()));
        conferenceRepository.save(conference);
        return new CreateResult(
            conference.getUuid(), conference.getFriendlyId(),
            conference.getName(), conference.getStatus().name().toLowerCase(),
            expiresAt != null ? expiresAt.toString() : null,
            conference.getLatitude(), conference.getLongitude(),
            conference.getEventDate(), conference.getVenue(),
            conference.getStartTime(), conference.getEndTime()
        );
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    private static Instant parseInstant(String s) {
        if (s == null || s.isBlank()) return null;
        try { return Instant.parse(s); } catch (Exception e) { return null; }
    }
}
