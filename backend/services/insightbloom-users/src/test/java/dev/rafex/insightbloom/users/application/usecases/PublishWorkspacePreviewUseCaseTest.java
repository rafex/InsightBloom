package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.Sandbox;
import dev.rafex.insightbloom.users.domain.ports.SandboxOrchestrator;
import dev.rafex.insightbloom.users.domain.ports.SandboxRepository;
import dev.rafex.insightbloom.users.domain.ports.WorkspacePreviewPublisher;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class PublishWorkspacePreviewUseCaseTest {

    @Test
    void reapprovisionsCliSeatBeforeDownloadingWorkspace() {
        final SandboxRepository sandboxRepository = Mockito.mock(SandboxRepository.class);
        final SandboxOrchestrator sandboxOrchestrator = Mockito.mock(SandboxOrchestrator.class);
        final WorkspacePreviewPublisher publisher = Mockito.mock(WorkspacePreviewPublisher.class);
        final Sandbox sandbox = new Sandbox("conf-1", 0, 2, Sandbox.VARIANT_CLI, "user-1",
                Instant.now().plusSeconds(3600));
        Mockito.when(sandboxRepository.findByConferenceAndUser("conf-1", "user-1"))
                .thenReturn(Optional.of(sandbox));
        Mockito.when(sandboxOrchestrator.downloadWorkspaceZip(sandbox.podName(), sandbox.getSeatIndex()))
                .thenReturn("zip".getBytes());
        Mockito.when(publisher.publish("conf-1", "user-1", "zip".getBytes(), 3600))
                .thenReturn(new WorkspacePreviewPublisher.PreviewPublication(
                        "publication-1", "https://preview.example/p/publication-1/",
                        Instant.now().plusSeconds(3600), "hash", 1));

        final var result = new PublishWorkspacePreviewUseCase(
                sandboxRepository, sandboxOrchestrator, publisher).execute("conf-1", "user-1", 3600);

        assertNotNull(result);
        Mockito.verify(sandboxOrchestrator).ensureSeatReady(
                sandbox.podName(), sandbox.getSeatIndex(), "user-1");
        Mockito.verify(sandboxOrchestrator).downloadWorkspaceZip(
                sandbox.podName(), sandbox.getSeatIndex());
    }

    @Test
    void doesNotUseSeatAgentForWebWorkspace() {
        final SandboxRepository sandboxRepository = Mockito.mock(SandboxRepository.class);
        final SandboxOrchestrator sandboxOrchestrator = Mockito.mock(SandboxOrchestrator.class);
        final WorkspacePreviewPublisher publisher = Mockito.mock(WorkspacePreviewPublisher.class);
        final Sandbox sandbox = new Sandbox("conf-1", 0, "user-1", Instant.now().plusSeconds(3600));
        Mockito.when(sandboxRepository.findByConferenceAndUser("conf-1", "user-1"))
                .thenReturn(Optional.of(sandbox));
        Mockito.when(sandboxOrchestrator.downloadWorkspaceZip(sandbox.podName(), sandbox.getSeatIndex()))
                .thenReturn("zip".getBytes());
        Mockito.when(publisher.publish("conf-1", "user-1", "zip".getBytes(), 3600))
                .thenReturn(new WorkspacePreviewPublisher.PreviewPublication(
                        "publication-1", "https://preview.example/p/publication-1/",
                        Instant.now().plusSeconds(3600), "hash", 1));

        assertNotNull(new PublishWorkspacePreviewUseCase(
                sandboxRepository, sandboxOrchestrator, publisher).execute("conf-1", "user-1", 3600));

        Mockito.verify(sandboxOrchestrator, Mockito.never()).ensureSeatReady(
                Mockito.anyString(), Mockito.anyInt(), Mockito.anyString());
    }

    @Test
    void clampsTtlToOneHourMaxRegardlessOfRequestedValue() {
        final SandboxRepository sandboxRepository = Mockito.mock(SandboxRepository.class);
        final SandboxOrchestrator sandboxOrchestrator = Mockito.mock(SandboxOrchestrator.class);
        final WorkspacePreviewPublisher publisher = Mockito.mock(WorkspacePreviewPublisher.class);
        final Sandbox sandbox = new Sandbox("conf-1", 0, "user-1", Instant.now().plusSeconds(3600));
        Mockito.when(sandboxRepository.findByConferenceAndUser("conf-1", "user-1"))
                .thenReturn(Optional.of(sandbox));
        Mockito.when(sandboxOrchestrator.downloadWorkspaceZip(sandbox.podName(), sandbox.getSeatIndex()))
                .thenReturn("zip".getBytes());
        Mockito.when(publisher.publish("conf-1", "user-1", "zip".getBytes(), 3600))
                .thenReturn(new WorkspacePreviewPublisher.PreviewPublication(
                        "publication-1", "https://preview.example/p/publication-1/",
                        Instant.now().plusSeconds(3600), "hash", 1));

        new PublishWorkspacePreviewUseCase(sandboxRepository, sandboxOrchestrator, publisher)
                .execute("conf-1", "user-1", 24 * 3600);

        Mockito.verify(publisher).publish("conf-1", "user-1", "zip".getBytes(), 3600);
    }
}
