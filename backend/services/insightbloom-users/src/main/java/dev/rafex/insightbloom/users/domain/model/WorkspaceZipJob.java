package dev.rafex.insightbloom.users.domain.model;

import java.time.Instant;

/**
 * Job asíncrono de armado del ZIP del workspace del alumno (2026-08) -- reemplaza el flujo
 * síncrono que hacía esperar al navegador la compresión completa (timeout real del pod: 90s, ver
 * KubernetesPodClient.downloadWorkspaceZip). {@code downloadToken} solo existe una vez el status
 * pasa a READY (ver StartWorkspaceZipJobUseCase); el ZIP en sí NO se persiste acá -- vive en
 * memoria (ver WorkspaceZipCache) mientras el job no expira.
 */
public record WorkspaceZipJob(
        String uuid,
        String conferenceUuid,
        String sandboxUuid,
        String userUuid,
        String status,
        String downloadToken,
        Instant createdAt,
        Instant readyAt,
        Instant expiresAt,
        String errorMessage) {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_READY = "READY";
    public static final String STATUS_FAILED = "FAILED";

    public boolean isExpired() {
        return expiresAt != null && expiresAt.isBefore(Instant.now());
    }
}
