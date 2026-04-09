package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.Conference;
import dev.rafex.insightbloom.users.domain.ports.ConferenceRepository;
import dev.rafex.insightbloom.users.domain.services.FriendlyIdService;

public class CreateConferenceUseCase {
    private final ConferenceRepository conferenceRepository;
    private final FriendlyIdService friendlyIdService;

    public CreateConferenceUseCase(ConferenceRepository conferenceRepository, FriendlyIdService friendlyIdService) {
        this.conferenceRepository = conferenceRepository;
        this.friendlyIdService = friendlyIdService;
    }

    public record CreateRequest(String name, String createdByUserUuid, Double latitude, Double longitude) {}
    public record CreateResult(String conferenceId, String friendlyId, String name, String status, Double latitude, Double longitude) {}

    public CreateResult execute(CreateRequest request) {
        String friendlyId = friendlyIdService.generate(request.name());
        Conference conference = new Conference(friendlyId, request.name(), request.createdByUserUuid(),
                request.latitude(), request.longitude());
        conferenceRepository.save(conference);
        return new CreateResult(conference.getUuid(), conference.getFriendlyId(),
                conference.getName(), conference.getStatus().name().toLowerCase(),
                conference.getLatitude(), conference.getLongitude());
    }
}
