package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.Conference;
import dev.rafex.insightbloom.users.domain.ports.CascadeDeletePort;
import dev.rafex.insightbloom.users.domain.ports.ConferenceRepository;

import java.util.List;
import java.util.Optional;

public class GetConferenceUseCase {
    private final ConferenceRepository conferenceRepository;
    private final CascadeDeletePort cascadeDeletePort;

    public GetConferenceUseCase(ConferenceRepository conferenceRepository, CascadeDeletePort cascadeDeletePort) {
        this.conferenceRepository = conferenceRepository;
        this.cascadeDeletePort = cascadeDeletePort;
    }

    public Optional<Conference> byId(String uuid) {
        return conferenceRepository.findByUuid(uuid);
    }

    public Optional<Conference> byFriendlyId(String friendlyId) {
        return conferenceRepository.findByFriendlyId(friendlyId);
    }

    public Optional<Conference> byShortCode(String shortCode) {
        return conferenceRepository.findByShortCode(shortCode);
    }

    public List<Conference> byUser(String userUuid) {
        return conferenceRepository.findByUser(userUuid);
    }

    public boolean delete(String uuid, String requestingUserUuid) {
        return conferenceRepository.findByUuid(uuid)
            .filter(c -> c.getCreatedByUserUuid().equals(requestingUserUuid))
            .map(c -> {
                conferenceRepository.delete(uuid);
                try {
                    cascadeDeletePort.deleteConferenceData(uuid);
                } catch (final Exception e) {
                    System.err.println("cascade_delete_error conference=" + uuid + " error=" + e.getMessage());
                }
                return true;
            })
            .orElse(false);
    }
}
