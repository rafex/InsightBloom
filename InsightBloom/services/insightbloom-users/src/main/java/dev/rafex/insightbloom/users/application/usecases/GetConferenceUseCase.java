package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.Conference;
import dev.rafex.insightbloom.users.domain.ports.ConferenceRepository;

import java.util.List;
import java.util.Optional;

public class GetConferenceUseCase {
    private final ConferenceRepository conferenceRepository;

    public GetConferenceUseCase(ConferenceRepository conferenceRepository) {
        this.conferenceRepository = conferenceRepository;
    }

    public Optional<Conference> byId(String uuid) {
        return conferenceRepository.findByUuid(uuid);
    }

    public Optional<Conference> byFriendlyId(String friendlyId) {
        return conferenceRepository.findByFriendlyId(friendlyId);
    }

    public List<Conference> byUser(String userUuid) {
        return conferenceRepository.findByUser(userUuid);
    }
}
