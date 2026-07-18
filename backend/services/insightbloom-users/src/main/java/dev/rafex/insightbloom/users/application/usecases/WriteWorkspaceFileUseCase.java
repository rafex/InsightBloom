package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.Sandbox;
import dev.rafex.insightbloom.users.domain.ports.SandboxOrchestrator;
import dev.rafex.insightbloom.users.domain.ports.SandboxRepository;

/**
 * Dashboard de moderador (Fase 4): guarda un archivo en el workspace activo de un alumno --
 * lectura y escritura completa (decision del usuario), sin locking real, solo deteccion de
 * conflicto por mtime (ver SandboxOrchestrator#writeWorkspaceFile / sandbox_file_api.py). Si el
 * archivo cambio desde que el moderador lo leyo, propaga {@code IllegalArgumentException(
 * "file_conflict")}; el organizador puede reintentar con {@code force=true} (expectedMtime
 * null), que sobreescribe sin chequeo.
 */
public class WriteWorkspaceFileUseCase {
    private final SandboxRepository sandboxRepository;
    private final SandboxOrchestrator sandboxOrchestrator;

    public WriteWorkspaceFileUseCase(final SandboxRepository sandboxRepository,
                                      final SandboxOrchestrator sandboxOrchestrator) {
        this.sandboxRepository = sandboxRepository;
        this.sandboxOrchestrator = sandboxOrchestrator;
    }

    public double execute(final String conferenceUuid, final String studentUserUuid, final String path,
                           final String content, final Double expectedMtime) {
        final Sandbox sandbox = sandboxRepository.findByConferenceAndUser(conferenceUuid, studentUserUuid)
                .orElseThrow(() -> new IllegalArgumentException("sandbox_not_assigned"));
        return sandboxOrchestrator.writeWorkspaceFile(sandbox.podName(), sandbox.getSeatIndex(), path, content, expectedMtime);
    }
}
