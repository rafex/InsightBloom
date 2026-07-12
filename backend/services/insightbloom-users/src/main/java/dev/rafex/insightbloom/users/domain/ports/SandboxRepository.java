package dev.rafex.insightbloom.users.domain.ports;

import dev.rafex.insightbloom.users.domain.model.Sandbox;

import java.util.List;
import java.util.Optional;

public interface SandboxRepository {
    void save(Sandbox sandbox);

    Optional<Sandbox> findByUuid(String uuid);

    List<Sandbox> findByConferenceUuid(String conferenceUuid);

    Optional<Sandbox> findUnassignedSlotForConference(String conferenceUuid);

    Optional<Sandbox> findByConferenceAndUser(String conferenceUuid, String userUuid);

    void deleteByConferenceUuid(String conferenceUuid);
}
