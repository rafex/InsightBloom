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

    public record CreateRequest(String name, String createdByUserUuid, String expiresAt) {}
    public record CreateResult(String conferenceId, String friendlyId, String name, String status, String expiresAt) {}

    public CreateResult execute(CreateRequest request) {
        String friendlyId = friendlyIdService.generate(request.name());
        Instant expiresAt = parseInstant(request.expiresAt());
        Conference conference = new Conference(friendlyId, request.name(), request.createdByUserUuid(), expiresAt);
        conferenceRepository.save(conference);
        return new CreateResult(
            conference.getUuid(), conference.getFriendlyId(),
            conference.getName(), conference.getStatus().name().toLowerCase(),
            expiresAt != null ? expiresAt.toString() : null
        );
    }

    private static Instant parseInstant(String s) {
        if (s == null || s.isBlank()) return null;
        try { return Instant.parse(s); } catch (Exception e) { return null; }
    }
}
