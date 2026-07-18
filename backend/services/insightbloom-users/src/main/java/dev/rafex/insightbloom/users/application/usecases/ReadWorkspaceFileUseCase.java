package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.Sandbox;
import dev.rafex.insightbloom.users.domain.model.WorkspaceFileContent;
import dev.rafex.insightbloom.users.domain.ports.SandboxOrchestrator;
import dev.rafex.insightbloom.users.domain.ports.SandboxRepository;

/** Dashboard de moderador (Fase 4): contenido de un archivo puntual del workspace activo de un
 *  alumno -- ver ListWorkspaceFilesUseCase para el resto del contexto. */
public class ReadWorkspaceFileUseCase {
    private final SandboxRepository sandboxRepository;
    private final SandboxOrchestrator sandboxOrchestrator;

    public ReadWorkspaceFileUseCase(final SandboxRepository sandboxRepository,
                                     final SandboxOrchestrator sandboxOrchestrator) {
        this.sandboxRepository = sandboxRepository;
        this.sandboxOrchestrator = sandboxOrchestrator;
    }

    public WorkspaceFileContent execute(final String conferenceUuid, final String studentUserUuid, final String path) {
        final Sandbox sandbox = sandboxRepository.findByConferenceAndUser(conferenceUuid, studentUserUuid)
                .orElseThrow(() -> new IllegalArgumentException("sandbox_not_assigned"));
        return sandboxOrchestrator.readWorkspaceFile(sandbox.podName(), sandbox.getSeatIndex(), path);
    }
}
