package dev.rafex.insightbloom.users.domain.ports;

import dev.rafex.insightbloom.users.domain.model.WorkspaceZipJob;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface WorkspaceZipJobRepository {
    WorkspaceZipJob save(WorkspaceZipJob job);

    Optional<WorkspaceZipJob> findByUuid(String uuid);

    /** Jobs con expires_at vencido (incluye PENDING/FAILED viejos, no solo READY) -- ver
     *  CleanupExpiredWorkspaceZipJobsUseCase. expires_at es NULL mientras el job sigue PENDING,
     *  así que un job atascado no expira solo por esto (se limpia aparte si hace falta). */
    List<WorkspaceZipJob> findExpired(Instant now);

    void deleteByUuid(String uuid);
}
