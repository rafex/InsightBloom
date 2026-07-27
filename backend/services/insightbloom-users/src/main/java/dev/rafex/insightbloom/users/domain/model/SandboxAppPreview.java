package dev.rafex.insightbloom.users.domain.model;

import java.time.Instant;

/**
 * Publicación pública de un backend/API REST que el alumno corre dentro de su sandbox (a
 * diferencia de {@code WorkspacePreviewPublisher}, que sirve un ZIP estático del workspace: acá
 * el proceso del alumno sigue vivo, insightbloom-tools-gateway lo proxea en tiempo real).
 *
 * Acceso: no requiere sesión de InsightBloom (a diferencia de IDE Web/CLI) -- requiere el
 * {@code accessToken} generado al publicar, con el mismo TTL que la publicación. Eso permite
 * probar la API desde herramientas externas (Postman, un grader) sin que el alumno comparta su
 * sesión, pero tampoco la deja abierta al mundo sin control.
 *
 * Un solo preview activo por sandbox: publicar de nuevo reemplaza el anterior (ver
 * SqliteSandboxAppPreviewRepository, UNIQUE(conference_uuid, user_uuid)).
 */
public record SandboxAppPreview(
        String uuid,
        String conferenceUuid,
        String userUuid,
        String podName,
        int targetPort,
        String accessToken,
        Instant createdAt,
        Instant expiresAt) {

    public boolean isExpired() {
        return expiresAt.isBefore(Instant.now());
    }
}
