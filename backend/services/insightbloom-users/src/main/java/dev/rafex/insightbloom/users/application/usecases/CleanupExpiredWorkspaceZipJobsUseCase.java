package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.WorkspaceZipJob;
import dev.rafex.insightbloom.users.domain.ports.WorkspaceZipJobRepository;
import dev.rafex.insightbloom.users.domain.services.WorkspaceZipCache;

import java.time.Instant;

/** Corrido periódicamente (ver UsersApplication) para cumplir el TTL de 2hs pedido: borra la fila
 * y libera el ZIP cacheado en memoria de los jobs vencidos. */
public class CleanupExpiredWorkspaceZipJobsUseCase {
    private final WorkspaceZipJobRepository jobRepository;
    private final WorkspaceZipCache zipCache;

    public CleanupExpiredWorkspaceZipJobsUseCase(final WorkspaceZipJobRepository jobRepository,
                                                  final WorkspaceZipCache zipCache) {
        this.jobRepository = jobRepository;
        this.zipCache = zipCache;
    }

    public int execute(final Instant now) {
        final var expired = jobRepository.findExpired(now);
        for (final WorkspaceZipJob job : expired) {
            zipCache.remove(job.uuid());
            jobRepository.deleteByUuid(job.uuid());
        }
        return expired.size();
    }
}
