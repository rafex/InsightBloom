package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.Sandbox;
import dev.rafex.insightbloom.users.domain.ports.SandboxRepository;

/**
 * Genera el link de descarga del workspace del alumno -- {@code downloadBaseUrl} apunta al host
 * publico de insightbloom-users (proxeado por el nginx del frontend, ej.
 * "https://insightbloom.v1.rafex.cloud/api/users"), NO al tools-gateway: el zip se arma llamando
 * al pod desde el propio insightbloom-users (mismo camino que ya usa
 * ListWorkspaceFilesUseCase/ReadWorkspaceFileUseCase), ver {@link DownloadWorkspaceZipUseCase} y
 * {@link WorkspaceDownloadToken} para el resto del flujo.
 */
public class GenerateWorkspaceDownloadUrlUseCase {
    private final SandboxRepository sandboxRepository;
    private final String downloadBaseUrl;
    private final WorkspaceDownloadToken tokenCodec;

    public GenerateWorkspaceDownloadUrlUseCase(final SandboxRepository sandboxRepository,
                                              final String downloadBaseUrl,
                                              final WorkspaceDownloadToken tokenCodec) {
        this.sandboxRepository = sandboxRepository;
        this.downloadBaseUrl = downloadBaseUrl;
        this.tokenCodec = tokenCodec;
    }

    public WorkspaceDownloadInfo execute(final String conferenceUuid, final String userUuid) {
        // Buscar sandbox asignado al usuario en esta conferencia
        final Sandbox sandbox = sandboxRepository
            .findByConferenceAndUser(conferenceUuid, userUuid)
            .orElseThrow(() -> new IllegalArgumentException("sandbox_not_assigned"));

        final String downloadToken = tokenCodec.encode(sandbox.getUuid(), userUuid);

        final String downloadUrl = String.format(
            "%s/workspaces/%s/download?token=%s",
            downloadBaseUrl,
            sandbox.getUuid(),
            downloadToken
        );

        return new WorkspaceDownloadInfo(sandbox.getUuid(), downloadUrl, WorkspaceDownloadToken.EXPIRY_SECONDS);
    }

    public static class WorkspaceDownloadInfo {
        public final String sandboxUuid;
        public final String downloadUrl;
        public final long expiresInSeconds;

        public WorkspaceDownloadInfo(final String sandboxUuid, final String downloadUrl,
                                   final long expiresInSeconds) {
            this.sandboxUuid = sandboxUuid;
            this.downloadUrl = downloadUrl;
            this.expiresInSeconds = expiresInSeconds;
        }
    }
}
