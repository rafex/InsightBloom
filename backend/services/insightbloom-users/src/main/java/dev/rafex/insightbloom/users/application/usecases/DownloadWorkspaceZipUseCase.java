package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.Sandbox;
import dev.rafex.insightbloom.users.domain.ports.SandboxOrchestrator;
import dev.rafex.insightbloom.users.domain.ports.SandboxRepository;

/**
 * Valida el token de descarga self-contained (ver {@link WorkspaceDownloadToken}) y arma el zip
 * del workspace del alumno -- llamado desde {@code WorkspaceDownloadHandler} (ruta publica, sin
 * Bearer, ver GenerateWorkspaceDownloadUrlUseCase) cuando el navegador navega directo al link de
 * descarga.
 */
public class DownloadWorkspaceZipUseCase {
    private final SandboxRepository sandboxRepository;
    private final SandboxOrchestrator sandboxOrchestrator;

    public DownloadWorkspaceZipUseCase(final SandboxRepository sandboxRepository,
                                        final SandboxOrchestrator sandboxOrchestrator) {
        this.sandboxRepository = sandboxRepository;
        this.sandboxOrchestrator = sandboxOrchestrator;
    }

    public byte[] execute(final String sandboxUuid, final String token) {
        final var parsed = WorkspaceDownloadToken.decode(token)
                .orElseThrow(() -> new IllegalArgumentException("token_invalid"));
        if (!parsed.sandboxUuid().equals(sandboxUuid)) {
            throw new IllegalArgumentException("token_invalid");
        }
        final Sandbox sandbox = sandboxRepository.findByUuid(sandboxUuid)
                .orElseThrow(() -> new IllegalArgumentException("sandbox_not_found"));
        if (!parsed.userUuid().equals(sandbox.getUserUuid())) {
            throw new IllegalArgumentException("token_invalid");
        }
        return sandboxOrchestrator.downloadWorkspaceZip(sandbox.podName(), sandbox.getSeatIndex());
    }
}
