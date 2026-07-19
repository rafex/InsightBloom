package dev.rafex.insightbloom.users.application.usecases;

import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

/**
 * Codifica/decodifica el token de descarga del workspace -- formato compartido entre
 * {@link GenerateWorkspaceDownloadUrlUseCase} (lo emite) y {@link DownloadWorkspaceZipUseCase}
 * (lo valida), para que ambos lados nunca se desincronicen del formato.
 *
 * No es un JWT firmado (ver comentario original en GenerateWorkspaceDownloadUrlUseCase, "en Fase
 * 5 se puede cambiar a JWT firmado") -- la validacion real de que el pedido es legitimo es que
 * el sandboxUuid/userUuid decodificados coincidan con el sandbox real (DownloadWorkspaceZipUseCase
 * los cruza contra {@code SandboxRepository.findByUuid}), mas la ventana de expiracion.
 */
final class WorkspaceDownloadToken {
    static final long EXPIRY_SECONDS = 3600; // 1 hora

    private WorkspaceDownloadToken() {
    }

    static String encode(final String sandboxUuid, final String userUuid) {
        final String payload = String.join(":",
                sandboxUuid,
                userUuid,
                String.valueOf(Instant.now().getEpochSecond()),
                UUID.randomUUID().toString()
        );
        return Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes());
    }

    /** {@code Optional.empty()} si el token esta mal formado, corrupto, o vencido. */
    static Optional<Parsed> decode(final String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        final String payload;
        try {
            payload = new String(Base64.getUrlDecoder().decode(token));
        } catch (final IllegalArgumentException e) {
            return Optional.empty();
        }
        final String[] parts = payload.split(":", 4);
        if (parts.length != 4) {
            return Optional.empty();
        }
        final long issuedAt;
        try {
            issuedAt = Long.parseLong(parts[2]);
        } catch (final NumberFormatException e) {
            return Optional.empty();
        }
        if (Instant.now().getEpochSecond() - issuedAt > EXPIRY_SECONDS) {
            return Optional.empty();
        }
        return Optional.of(new Parsed(parts[0], parts[1]));
    }

    record Parsed(String sandboxUuid, String userUuid) {
    }
}
