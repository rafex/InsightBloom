package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.Sandbox;
import dev.rafex.insightbloom.users.domain.ports.SandboxRepository;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

public class GenerateWorkspaceDownloadUrlUseCase {
    private final SandboxRepository sandboxRepository;
    private final String gatewayBaseUrl;
    private static final long DOWNLOAD_TOKEN_EXPIRY_SECONDS = 3600; // 1 hora

    public GenerateWorkspaceDownloadUrlUseCase(final SandboxRepository sandboxRepository,
                                              final String gatewayBaseUrl) {
        this.sandboxRepository = sandboxRepository;
        this.gatewayBaseUrl = gatewayBaseUrl;
    }

    public WorkspaceDownloadInfo execute(final String conferenceUuid, final String userUuid) {
        // Buscar sandbox asignado al usuario en esta conferencia
        final Sandbox sandbox = sandboxRepository
            .findByConferenceAndUser(conferenceUuid, userUuid)
            .orElseThrow(() -> new IllegalArgumentException("sandbox_not_assigned"));

        // Generar token temporal de descarga
        final String downloadToken = generateDownloadToken(sandbox.getUuid(), userUuid);
        final long expiresIn = DOWNLOAD_TOKEN_EXPIRY_SECONDS;

        // URL para gateway (proxea a code-server en Fase 4+)
        final String downloadUrl = String.format(
            "%s/workspaces/%s/download?token=%s",
            gatewayBaseUrl,
            sandbox.getUuid(),
            downloadToken
        );

        return new WorkspaceDownloadInfo(sandbox.getUuid(), downloadUrl, expiresIn);
    }

    private String generateDownloadToken(final String sandboxUuid, final String userUuid) {
        // Simple token = base64(sandboxUuid:userUuid:timestamp:nonce)
        // En Fase 5 se puede cambiar a JWT firmado
        final String payload = String.join(":",
            sandboxUuid,
            userUuid,
            String.valueOf(Instant.now().getEpochSecond()),
            UUID.randomUUID().toString()
        );
        return Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes());
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
