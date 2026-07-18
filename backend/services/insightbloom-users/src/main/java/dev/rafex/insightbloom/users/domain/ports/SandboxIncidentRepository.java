package dev.rafex.insightbloom.users.domain.ports;

import dev.rafex.insightbloom.users.domain.model.SandboxIncident;

import java.util.List;

public interface SandboxIncidentRepository {
    void save(SandboxIncident incident);

    /** Mas reciente primero -- lo que el organizador quiere ver arriba en el Dashboard. */
    List<SandboxIncident> findByConferenceUuid(String conferenceUuid);
}
