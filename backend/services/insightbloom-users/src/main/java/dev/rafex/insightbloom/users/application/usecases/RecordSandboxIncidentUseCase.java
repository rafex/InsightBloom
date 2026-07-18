package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.SandboxIncident;
import dev.rafex.insightbloom.users.domain.ports.SandboxIncidentRepository;

/** Invocado por el endpoint interno {@code POST /internal/sandbox-incidents} -- ver
 *  {@code sandbox-agent.py}'s watchdog y DEC-0025. */
public class RecordSandboxIncidentUseCase {
    private final SandboxIncidentRepository sandboxIncidentRepository;

    public RecordSandboxIncidentUseCase(final SandboxIncidentRepository sandboxIncidentRepository) {
        this.sandboxIncidentRepository = sandboxIncidentRepository;
    }

    public SandboxIncident execute(final String conferenceUuid, final String podName, final int seatIndex,
                                    final String userUuid, final String type, final String detail) {
        if (conferenceUuid == null || conferenceUuid.isBlank()) {
            throw new IllegalArgumentException("conference_uuid_required");
        }
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("type_required");
        }
        final var incident = new SandboxIncident(conferenceUuid, podName, seatIndex, userUuid, type, detail);
        sandboxIncidentRepository.save(incident);
        return incident;
    }
}
