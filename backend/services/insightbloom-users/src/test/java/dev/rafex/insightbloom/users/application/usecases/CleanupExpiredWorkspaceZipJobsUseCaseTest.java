package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.WorkspaceZipJob;
import dev.rafex.insightbloom.users.domain.ports.WorkspaceZipJobRepository;
import dev.rafex.insightbloom.users.domain.services.WorkspaceZipCache;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CleanupExpiredWorkspaceZipJobsUseCaseTest {
    @Test
    void deletesExpiredJobsAndEvictsCachedBytes() {
        final WorkspaceZipJobRepository repository = mock(WorkspaceZipJobRepository.class);
        final WorkspaceZipCache cache = new WorkspaceZipCache();
        cache.put("job-1", new byte[]{1});
        cache.put("job-2", new byte[]{2});
        final WorkspaceZipJob job1 = new WorkspaceZipJob("job-1", "conf-1", "sandbox-1", "user-1",
                WorkspaceZipJob.STATUS_READY, "tok", Instant.now(), Instant.now(), Instant.now().minusSeconds(1), null);
        final WorkspaceZipJob job2 = new WorkspaceZipJob("job-2", "conf-1", "sandbox-2", "user-2",
                WorkspaceZipJob.STATUS_READY, "tok2", Instant.now(), Instant.now(), Instant.now().minusSeconds(5), null);
        final Instant now = Instant.now();
        when(repository.findExpired(now)).thenReturn(List.of(job1, job2));

        final int cleaned = new CleanupExpiredWorkspaceZipJobsUseCase(repository, cache).execute(now);

        assertEquals(2, cleaned);
        verify(repository).deleteByUuid("job-1");
        verify(repository).deleteByUuid("job-2");
        assertNull(cache.get("job-1"));
        assertNull(cache.get("job-2"));
    }

    @Test
    void noopWhenNothingExpired() {
        final WorkspaceZipJobRepository repository = mock(WorkspaceZipJobRepository.class);
        final Instant now = Instant.now();
        when(repository.findExpired(now)).thenReturn(List.of());

        final int cleaned = new CleanupExpiredWorkspaceZipJobsUseCase(repository, new WorkspaceZipCache()).execute(now);

        assertEquals(0, cleaned);
    }
}
