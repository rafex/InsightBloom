package dev.rafex.insightbloom.users.domain.ports;

import dev.rafex.insightbloom.users.domain.model.Sandbox;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SandboxRepository {
    /** INSERT real (no upsert): lanza en violación de UNIQUE(conference_uuid, sandbox_slot) —
     *  es la señal de que otro request ganó la carrera por ese slot. */
    void save(Sandbox sandbox);

    Optional<Sandbox> findByUuid(String uuid);

    List<Sandbox> findByConferenceUuid(String conferenceUuid);

    Optional<Sandbox> findByConferenceAndUser(String conferenceUuid, String userUuid);

    List<Sandbox> findExpired(Instant now);

    void deleteByConferenceUuid(String conferenceUuid);

    int deleteExpired(Instant now);
}
