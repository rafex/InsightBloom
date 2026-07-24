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

    /**
     * Extiende el vencimiento de un sandbox activo -- se llama al reconectarse a un sandbox ya
     * existente (ver AssignSandboxUseCase) para que una sesión en uso activo no expire a mitad
     * de camino solo porque pasaron N horas desde la PRIMERA vez que se creó (incidente
     * 2026-07-19: sandboxes desaparecían sin aviso durante una sesión larga).
     */
    void updateExpiresAt(String uuid, Instant expiresAt);

    void deleteByConferenceUuid(String conferenceUuid);

    /** Libera un sandbox puntual -- usado al cambiar de variante (un alumno tiene un solo
     *  sandbox activo a la vez, ver AssignSandboxUseCase). */
    void deleteByUuid(String uuid);

    /** Elimina todas las filas que representan el mismo Pod (incluye todos sus asientos CLI). */
    void deletePod(String conferenceUuid, String variant, int sandboxSlot);

    int deleteExpired(Instant now);
}
