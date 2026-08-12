package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.WorkspaceZipJob;
import dev.rafex.insightbloom.users.domain.ports.WorkspaceZipJobRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GetWorkspaceZipJobStatusUseCaseTest {
    @Test
    void returnsDownloadUrlWhenReadyAndNotExpired() {
        final WorkspaceZipJobRepository repository = mock(WorkspaceZipJobRepository.class);
        final WorkspaceZipJob job = new WorkspaceZipJob("job-1", "conf-1", "sandbox-1", "user-1",
                WorkspaceZipJob.STATUS_READY, "tok123", Instant.now(), Instant.now(),
                Instant.now().plusSeconds(3600), null);
        when(repository.findByUuid("job-1")).thenReturn(Optional.of(job));

        final var result = new GetWorkspaceZipJobStatusUseCase(repository, "https://x.example/api/users")
                .execute("job-1", "user-1");

        assertEquals("READY", result.status());
        assertEquals("https://x.example/api/users/workspaces/job-1/download?token=tok123", result.downloadUrl());
    }

    @Test
    void returnsExpiredWhenReadyButPastExpiry() {
        final WorkspaceZipJobRepository repository = mock(WorkspaceZipJobRepository.class);
        final WorkspaceZipJob job = new WorkspaceZipJob("job-1", "conf-1", "sandbox-1", "user-1",
                WorkspaceZipJob.STATUS_READY, "tok123", Instant.now().minusSeconds(10000),
                Instant.now().minusSeconds(9000), Instant.now().minusSeconds(1), null);
        when(repository.findByUuid("job-1")).thenReturn(Optional.of(job));

        final var result = new GetWorkspaceZipJobStatusUseCase(repository, "https://x.example/api/users")
                .execute("job-1", "user-1");

        assertEquals("EXPIRED", result.status());
        assertNull(result.downloadUrl());
    }

    @Test
    void returnsPendingWithoutDownloadUrl() {
        final WorkspaceZipJobRepository repository = mock(WorkspaceZipJobRepository.class);
        final WorkspaceZipJob job = new WorkspaceZipJob("job-1", "conf-1", "sandbox-1", "user-1",
                WorkspaceZipJob.STATUS_PENDING, null, Instant.now(), null, null, null);
        when(repository.findByUuid("job-1")).thenReturn(Optional.of(job));

        final var result = new GetWorkspaceZipJobStatusUseCase(repository, "https://x.example/api/users")
                .execute("job-1", "user-1");

        assertEquals("PENDING", result.status());
        assertNull(result.downloadUrl());
    }

    @Test
    void returnsFailedWithErrorMessage() {
        final WorkspaceZipJobRepository repository = mock(WorkspaceZipJobRepository.class);
        final WorkspaceZipJob job = new WorkspaceZipJob("job-1", "conf-1", "sandbox-1", "user-1",
                WorkspaceZipJob.STATUS_FAILED, null, Instant.now(), null, null, "workspace_too_large");
        when(repository.findByUuid("job-1")).thenReturn(Optional.of(job));

        final var result = new GetWorkspaceZipJobStatusUseCase(repository, "https://x.example/api/users")
                .execute("job-1", "user-1");

        assertEquals("FAILED", result.status());
        assertEquals("workspace_too_large", result.errorMessage());
    }

    @Test
    void rejectsWhenJobBelongsToAnotherUser() {
        final WorkspaceZipJobRepository repository = mock(WorkspaceZipJobRepository.class);
        final WorkspaceZipJob job = new WorkspaceZipJob("job-1", "conf-1", "sandbox-1", "owner-user",
                WorkspaceZipJob.STATUS_READY, "tok", Instant.now(), Instant.now(),
                Instant.now().plusSeconds(3600), null);
        when(repository.findByUuid("job-1")).thenReturn(Optional.of(job));

        final var useCase = new GetWorkspaceZipJobStatusUseCase(repository, "https://x.example/api/users");
        assertThrows(IllegalArgumentException.class, () -> useCase.execute("job-1", "intruder"));
    }

    @Test
    void rejectsUnknownJob() {
        final WorkspaceZipJobRepository repository = mock(WorkspaceZipJobRepository.class);
        when(repository.findByUuid("missing")).thenReturn(Optional.empty());
        final var useCase = new GetWorkspaceZipJobStatusUseCase(repository, "https://x.example/api/users");
        assertThrows(IllegalArgumentException.class, () -> useCase.execute("missing", "user-1"));
    }
}
