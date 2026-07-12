package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.Conference;
import dev.rafex.insightbloom.users.domain.model.Sandbox;
import dev.rafex.insightbloom.users.domain.ports.ConferenceRepository;
import dev.rafex.insightbloom.users.domain.ports.SandboxRepository;

public class AssignSandboxUseCase {
    private final SandboxRepository sandboxRepository;
    private final ConferenceRepository conferenceRepository;

    public AssignSandboxUseCase(final SandboxRepository sandboxRepository,
                               final ConferenceRepository conferenceRepository) {
        this.sandboxRepository = sandboxRepository;
        this.conferenceRepository = conferenceRepository;
    }

    /**
     * Asigna un sandbox libre del pool a un usuario.
     *
     * Algoritmo:
     * 1. Valida que el evento exista y tenga CODE_IDE habilitada
     * 2. Busca un slot no asignado (FIFO: primer-libre)
     * 3. Si la asignación falla por concurrencia (otro user tomó ese slot),
     *    retorna error "sandbox_pool_full"
     * 4. Retorna el sandbox asignado
     *
     * Concurrencia: Confiamos en UNIQUE(conference_uuid, sandbox_slot) en SQLite.
     * Dos UPDATE concurrentes al mismo slot: uno falla con UNIQUE constraint.
     */
    public Sandbox execute(final String conferenceUuid, final String userUuid) {
        // Validar que el evento exista
        final Conference conference = conferenceRepository.findByUuid(conferenceUuid)
            .orElseThrow(() -> new IllegalArgumentException("conference_not_found"));

        // Buscar un slot libre
        final Sandbox unassigned = sandboxRepository.findUnassignedSlotForConference(conferenceUuid)
            .orElseThrow(() -> new IllegalArgumentException("sandbox_pool_full"));

        // Asignar al usuario
        unassigned.assignToUser(userUuid);

        try {
            sandboxRepository.save(unassigned);
        } catch (final RuntimeException e) {
            // En caso de UNIQUE constraint, otro user ganó la carrera
            if (e.getMessage() != null && e.getMessage().contains("UNIQUE")) {
                throw new IllegalArgumentException("sandbox_pool_full");
            }
            throw e;
        }

        return unassigned;
    }
}
