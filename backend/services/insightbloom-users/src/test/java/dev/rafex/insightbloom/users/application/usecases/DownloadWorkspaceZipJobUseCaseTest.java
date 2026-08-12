package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.WorkspaceZipJob;
import dev.rafex.insightbloom.users.domain.ports.WorkspaceZipJobRepository;
import dev.rafex.insightbloom.users.domain.services.WorkspaceZipCache;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DownloadWorkspaceZipJobUseCaseTest {
    @Test
    void servesCachedBytesWhenReadyAndTokenMatches() {
        final WorkspaceZipJobRepository repository = mock(WorkspaceZipJobRepository.class);
        final WorkspaceZipCache cache = new WorkspaceZipCache();
        final WorkspaceZipJob job = new WorkspaceZipJob("job-1", "conf-1", "sandbox-1", "user-1",
                WorkspaceZipJob.STATUS_READY, "tok123", Instant.now(), Instant.now(),
                Instant.now().plusSeconds(3600), null);
        when(repository.findByUuid("job-1")).thenReturn(Optional.of(job));
        cache.put("job-1", new byte[]{1, 2, 3});

        final byte[] result = new DownloadWorkspaceZipJobUseCase(repository, cache).execute("job-1", "tok123");

        assertArrayEquals(new byte[]{1, 2, 3}, result);
    }

    @Test
    void throwsJobNotFoundForUnknownUuid_soHandlerCanFallBackToLegacyFlow() {
        final WorkspaceZipJobRepository repository = mock(WorkspaceZipJobRepository.class);
        when(repository.findByUuid("unknown")).thenReturn(Optional.empty());

        final var useCase = new DownloadWorkspaceZipJobUseCase(repository, new WorkspaceZipCache());
        final var ex = assertThrows(IllegalArgumentException.class, () -> useCase.execute("unknown", "tok"));
        assertEquals("job_not_found", ex.getMessage());
    }

    @Test
    void rejectsWrongToken() {
        final WorkspaceZipJobRepository repository = mock(WorkspaceZipJobRepository.class);
        final WorkspaceZipJob job = new WorkspaceZipJob("job-1", "conf-1", "sandbox-1", "user-1",
                WorkspaceZipJob.STATUS_READY, "tok123", Instant.now(), Instant.now(),
                Instant.now().plusSeconds(3600), null);
        when(repository.findByUuid("job-1")).thenReturn(Optional.of(job));

        final var useCase = new DownloadWorkspaceZipJobUseCase(repository, new WorkspaceZipCache());
        final var ex = assertThrows(IllegalArgumentException.class, () -> useCase.execute("job-1", "wrong-token"));
        assertEquals("token_invalid", ex.getMessage());
    }

    @Test
    void rejectsWhenJobNotReadyYet() {
        final WorkspaceZipJobRepository repository = mock(WorkspaceZipJobRepository.class);
        final WorkspaceZipJob job = new WorkspaceZipJob("job-1", "conf-1", "sandbox-1", "user-1",
                WorkspaceZipJob.STATUS_PENDING, null, Instant.now(), null, null, null);
        when(repository.findByUuid("job-1")).thenReturn(Optional.of(job));

        final var useCase = new DownloadWorkspaceZipJobUseCase(repository, new WorkspaceZipCache());
        final var ex = assertThrows(IllegalArgumentException.class, () -> useCase.execute("job-1", "tok"));
        assertEquals("job_not_ready", ex.getMessage());
    }

    @Test
    void rejectsWhenExpired() {
        final WorkspaceZipJobRepository repository = mock(WorkspaceZipJobRepository.class);
        final WorkspaceZipJob job = new WorkspaceZipJob("job-1", "conf-1", "sandbox-1", "user-1",
                WorkspaceZipJob.STATUS_READY, "tok123", Instant.now().minusSeconds(10000),
                Instant.now().minusSeconds(9000), Instant.now().minusSeconds(1), null);
        when(repository.findByUuid("job-1")).thenReturn(Optional.of(job));

        final var useCase = new DownloadWorkspaceZipJobUseCase(repository, new WorkspaceZipCache());
        final var ex = assertThrows(IllegalArgumentException.class, () -> useCase.execute("job-1", "tok123"));
        assertEquals("job_expired", ex.getMessage());
    }

    @Test
    void rejectsWhenBytesEvictedFromCache() {
        final WorkspaceZipJobRepository repository = mock(WorkspaceZipJobRepository.class);
        final WorkspaceZipJob job = new WorkspaceZipJob("job-1", "conf-1", "sandbox-1", "user-1",
                WorkspaceZipJob.STATUS_READY, "tok123", Instant.now(), Instant.now(),
                Instant.now().plusSeconds(3600), null);
        when(repository.findByUuid("job-1")).thenReturn(Optional.of(job));

        final var useCase = new DownloadWorkspaceZipJobUseCase(repository, new WorkspaceZipCache());
        final var ex = assertThrows(IllegalArgumentException.class, () -> useCase.execute("job-1", "tok123"));
        assertEquals("zip_unavailable", ex.getMessage());
    }
}
