package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.SandboxIncident;
import dev.rafex.insightbloom.users.domain.ports.SandboxIncidentRepository;

import java.util.List;

/** Le muestra al organizador que asientos de sus Pods compartidos tuvieron que ser terminados
 *  por abuso de recursos -- ver DEC-0025. */
public class ListSandboxIncidentsUseCase {
    private final SandboxIncidentRepository sandboxIncidentRepository;

    public ListSandboxIncidentsUseCase(final SandboxIncidentRepository sandboxIncidentRepository) {
        this.sandboxIncidentRepository = sandboxIncidentRepository;
    }

    public List<SandboxIncident> execute(final String conferenceUuid) {
        return sandboxIncidentRepository.findByConferenceUuid(conferenceUuid);
    }
}
