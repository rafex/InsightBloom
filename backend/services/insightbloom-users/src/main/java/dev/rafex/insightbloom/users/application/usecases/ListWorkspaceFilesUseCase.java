package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.Sandbox;
import dev.rafex.insightbloom.users.domain.model.WorkspaceFileEntry;
import dev.rafex.insightbloom.users.domain.ports.SandboxOrchestrator;
import dev.rafex.insightbloom.users.domain.ports.SandboxRepository;

import java.util.List;

/** Dashboard de moderador (Fase 4): arbol de archivos del workspace ACTIVO de un alumno --
 *  resuelve su {@link Sandbox} (uno solo, cualquiera sea la variante, ver AssignSandboxUseCase)
 *  y delega en el agente HTTP dentro del Pod (ver SandboxOrchestrator#listWorkspaceFiles). */
public class ListWorkspaceFilesUseCase {
    private final SandboxRepository sandboxRepository;
    private final SandboxOrchestrator sandboxOrchestrator;

    public ListWorkspaceFilesUseCase(final SandboxRepository sandboxRepository,
                                      final SandboxOrchestrator sandboxOrchestrator) {
        this.sandboxRepository = sandboxRepository;
        this.sandboxOrchestrator = sandboxOrchestrator;
    }

    public List<WorkspaceFileEntry> execute(final String conferenceUuid, final String studentUserUuid, final String path) {
        final Sandbox sandbox = sandboxRepository.findByConferenceAndUser(conferenceUuid, studentUserUuid)
                .orElseThrow(() -> new IllegalArgumentException("sandbox_not_assigned"));
        return sandboxOrchestrator.listWorkspaceFiles(sandbox.podName(), sandbox.getSeatIndex(), path);
    }
}
