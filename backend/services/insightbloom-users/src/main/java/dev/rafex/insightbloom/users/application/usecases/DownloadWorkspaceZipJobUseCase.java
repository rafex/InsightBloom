package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.WorkspaceZipJob;
import dev.rafex.insightbloom.users.domain.ports.WorkspaceZipJobRepository;
import dev.rafex.insightbloom.users.domain.services.WorkspaceZipCache;

/**
 * Sirve el ZIP ya armado por StartWorkspaceZipJobUseCase -- llamado desde WorkspaceDownloadHandler
 * (ruta pública, sin Bearer, mismo criterio que el flujo legacy por sandbox uuid que sigue
 * intacto al lado de este). "job_not_found" se usa a propósito como señal para que el handler
 * caiga al flujo legacy si el uuid de la ruta no es un job (ver WorkspaceDownloadHandler.get()).
 */
public class DownloadWorkspaceZipJobUseCase {
    private final WorkspaceZipJobRepository jobRepository;
    private final WorkspaceZipCache zipCache;

    public DownloadWorkspaceZipJobUseCase(final WorkspaceZipJobRepository jobRepository,
                                           final WorkspaceZipCache zipCache) {
        this.jobRepository = jobRepository;
        this.zipCache = zipCache;
    }

    public byte[] execute(final String jobUuid, final String token) {
        final WorkspaceZipJob job = jobRepository.findByUuid(jobUuid)
                .orElseThrow(() -> new IllegalArgumentException("job_not_found"));
        if (!WorkspaceZipJob.STATUS_READY.equals(job.status())) {
            throw new IllegalArgumentException("job_not_ready");
        }
        if (job.isExpired()) {
            throw new IllegalArgumentException("job_expired");
        }
        if (job.downloadToken() == null || !java.security.MessageDigest.isEqual(
                job.downloadToken().getBytes(java.nio.charset.StandardCharsets.UTF_8),
                (token == null ? "" : token).getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
            throw new IllegalArgumentException("token_invalid");
        }
        final byte[] zip = zipCache.get(jobUuid);
        if (zip == null) {
            throw new IllegalArgumentException("zip_unavailable");
        }
        return zip;
    }
}
