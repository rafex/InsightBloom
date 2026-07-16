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

    /** Primer sandbox pre-provisionado (user_uuid nulo) de la conferencia, si existe. */
    Optional<Sandbox> findUnassigned(String conferenceUuid);

    /** Reclama un sandbox pre-provisionado de forma atomica: UPDATE ... WHERE user_uuid IS NULL.
     *  Devuelve {@code false} si otro request ya lo reclamo primero (no lanza, no es un error). */
    boolean claim(String uuid, String userUuid, Instant assignedAt);

    List<Sandbox> findExpired(Instant now);

    void deleteByConferenceUuid(String conferenceUuid);

    int deleteExpired(Instant now);
}
