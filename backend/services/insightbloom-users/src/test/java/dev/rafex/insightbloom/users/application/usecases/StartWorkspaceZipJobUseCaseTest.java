package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.Conference;
import dev.rafex.insightbloom.users.domain.model.Sandbox;
import dev.rafex.insightbloom.users.domain.model.User;
import dev.rafex.insightbloom.users.domain.model.UserRole;
import dev.rafex.insightbloom.users.domain.model.WorkspaceZipJob;
import dev.rafex.insightbloom.users.domain.ports.ConferenceRepository;
import dev.rafex.insightbloom.users.domain.ports.EmailPort;
import dev.rafex.insightbloom.users.domain.ports.SandboxOrchestrator;
import dev.rafex.insightbloom.users.domain.ports.SandboxRepository;
import dev.rafex.insightbloom.users.domain.ports.UserRepository;
import dev.rafex.insightbloom.users.domain.ports.WorkspaceZipJobRepository;
import dev.rafex.insightbloom.users.domain.services.NotificationStreamRegistry;
import dev.rafex.insightbloom.users.domain.services.WorkspaceZipCache;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class StartWorkspaceZipJobUseCaseTest {
    // Ejecuta el Runnable inline en vez de un thread real -- así el test puede verificar el
    // resultado final del job sin sleeps/polling.
    private static final ExecutorService DIRECT_EXECUTOR = new java.util.concurrent.AbstractExecutorService() {
        @Override public void shutdown() { }
        @Override public java.util.List<Runnable> shutdownNow() { return java.util.List.of(); }
        @Override public boolean isShutdown() { return false; }
        @Override public boolean isTerminated() { return false; }
        @Override public boolean awaitTermination(long timeout, java.util.concurrent.TimeUnit unit) { return true; }
        @Override public void execute(final Runnable command) { command.run(); }
    };

    private static Sandbox sandbox(final String userUuid) {
        return new Sandbox("conf-1", 0, 0, userUuid, Instant.now().plusSeconds(3600));
    }

    @Test
    void buildsZipAndMarksJobReady() {
        final SandboxRepository sandboxRepository = mock(SandboxRepository.class);
        final ConferenceRepository conferenceRepository = mock(ConferenceRepository.class);
        final UserRepository userRepository = mock(UserRepository.class);
        final WorkspaceZipJobRepository jobRepository = mock(WorkspaceZipJobRepository.class);
        final SandboxOrchestrator sandboxOrchestrator = mock(SandboxOrchestrator.class);
        final EmailPort emailPort = mock(EmailPort.class);
        final WorkspaceZipCache zipCache = new WorkspaceZipCache();
        final SendNotificationUseCase sendNotificationUseCase =
                new SendNotificationUseCase(mock(dev.rafex.insightbloom.users.domain.ports.NotificationRepository.class),
                        new NotificationStreamRegistry());

        final Sandbox sandbox = sandbox("user-1");
        when(sandboxRepository.findByConferenceAndUser("conf-1", "user-1")).thenReturn(Optional.of(sandbox));
        when(sandboxOrchestrator.downloadWorkspaceZip(sandbox.podName(), sandbox.getSeatIndex()))
                .thenReturn(new byte[]{1, 2, 3});
        final Conference conference = new Conference("evento", "Evento Demo", "owner");
        when(conferenceRepository.findByUuid("conf-1")).thenReturn(Optional.of(conference));
        when(emailPort.isEnabled()).thenReturn(false);

        final var useCase = new StartWorkspaceZipJobUseCase(sandboxRepository, conferenceRepository, userRepository,
                jobRepository, zipCache, sandboxOrchestrator, sendNotificationUseCase, emailPort, DIRECT_EXECUTOR,
                "https://insightbloom.example/api/users");

        final WorkspaceZipJob created = useCase.execute("conf-1", "user-1");

        assertEquals(WorkspaceZipJob.STATUS_PENDING, created.status());
        assertNotNull(created.uuid());

        final var jobCaptor = org.mockito.ArgumentCaptor.forClass(WorkspaceZipJob.class);
        verify(jobRepository, times(2)).save(jobCaptor.capture());
        final WorkspaceZipJob readyJob = jobCaptor.getAllValues().get(1);
        assertEquals(WorkspaceZipJob.STATUS_READY, readyJob.status());
        assertNotNull(readyJob.downloadToken());
        assertNotNull(readyJob.expiresAt());
        assertArrayEquals(new byte[]{1, 2, 3}, zipCache.get(created.uuid()));
    }

    @Test
    void marksJobFailedWhenOrchestratorThrows() {
        final SandboxRepository sandboxRepository = mock(SandboxRepository.class);
        final ConferenceRepository conferenceRepository = mock(ConferenceRepository.class);
        final UserRepository userRepository = mock(UserRepository.class);
        final WorkspaceZipJobRepository jobRepository = mock(WorkspaceZipJobRepository.class);
        final SandboxOrchestrator sandboxOrchestrator = mock(SandboxOrchestrator.class);
        final EmailPort emailPort = mock(EmailPort.class);
        final WorkspaceZipCache zipCache = new WorkspaceZipCache();
        final SendNotificationUseCase sendNotificationUseCase =
                new SendNotificationUseCase(mock(dev.rafex.insightbloom.users.domain.ports.NotificationRepository.class),
                        new NotificationStreamRegistry());

        final Sandbox sandbox = sandbox("user-1");
        when(sandboxRepository.findByConferenceAndUser("conf-1", "user-1")).thenReturn(Optional.of(sandbox));
        when(sandboxOrchestrator.downloadWorkspaceZip(sandbox.podName(), sandbox.getSeatIndex()))
                .thenThrow(new IllegalArgumentException("workspace_too_large"));

        final var useCase = new StartWorkspaceZipJobUseCase(sandboxRepository, conferenceRepository, userRepository,
                jobRepository, zipCache, sandboxOrchestrator, sendNotificationUseCase, emailPort, DIRECT_EXECUTOR,
                "https://insightbloom.example/api/users");

        final WorkspaceZipJob created = useCase.execute("conf-1", "user-1");

        final var jobCaptor = org.mockito.ArgumentCaptor.forClass(WorkspaceZipJob.class);
        verify(jobRepository, times(2)).save(jobCaptor.capture());
        final WorkspaceZipJob failedJob = jobCaptor.getAllValues().get(1);
        assertEquals(WorkspaceZipJob.STATUS_FAILED, failedJob.status());
        assertEquals("workspace_too_large", failedJob.errorMessage());
        assertNull(zipCache.get(created.uuid()));
        verifyNoInteractions(emailPort);
    }

    @Test
    void throwsWhenNoSandboxAssigned() {
        final SandboxRepository sandboxRepository = mock(SandboxRepository.class);
        when(sandboxRepository.findByConferenceAndUser("conf-1", "user-1")).thenReturn(Optional.empty());
        final var useCase = new StartWorkspaceZipJobUseCase(sandboxRepository, mock(ConferenceRepository.class),
                mock(UserRepository.class), mock(WorkspaceZipJobRepository.class), new WorkspaceZipCache(),
                mock(SandboxOrchestrator.class),
                new SendNotificationUseCase(mock(dev.rafex.insightbloom.users.domain.ports.NotificationRepository.class), new NotificationStreamRegistry()),
                mock(EmailPort.class), DIRECT_EXECUTOR, "https://insightbloom.example/api/users");

        assertThrows(IllegalArgumentException.class, () -> useCase.execute("conf-1", "user-1"));
    }

    @Test
    void sendsEmailWhenEnabledAndUserHasAddress() {
        final SandboxRepository sandboxRepository = mock(SandboxRepository.class);
        final ConferenceRepository conferenceRepository = mock(ConferenceRepository.class);
        final UserRepository userRepository = mock(UserRepository.class);
        final WorkspaceZipJobRepository jobRepository = mock(WorkspaceZipJobRepository.class);
        final SandboxOrchestrator sandboxOrchestrator = mock(SandboxOrchestrator.class);
        final EmailPort emailPort = mock(EmailPort.class);
        final WorkspaceZipCache zipCache = new WorkspaceZipCache();
        final SendNotificationUseCase sendNotificationUseCase =
                new SendNotificationUseCase(mock(dev.rafex.insightbloom.users.domain.ports.NotificationRepository.class),
                        new NotificationStreamRegistry());

        final Sandbox sandbox = sandbox("user-1");
        when(sandboxRepository.findByConferenceAndUser("conf-1", "user-1")).thenReturn(Optional.of(sandbox));
        when(sandboxOrchestrator.downloadWorkspaceZip(sandbox.podName(), sandbox.getSeatIndex()))
                .thenReturn(new byte[]{9});
        final Conference conference = new Conference("evento", "Evento Demo", "owner");
        when(conferenceRepository.findByUuid("conf-1")).thenReturn(Optional.of(conference));
        when(emailPort.isEnabled()).thenReturn(true);
        final User user = new User("user-1", "alumno", "Alumno Demo", "alumno@example.com", UserRole.ATTENDEE);
        when(userRepository.findByUuid("user-1")).thenReturn(Optional.of(user));

        final var useCase = new StartWorkspaceZipJobUseCase(sandboxRepository, conferenceRepository, userRepository,
                jobRepository, zipCache, sandboxOrchestrator, sendNotificationUseCase, emailPort, DIRECT_EXECUTOR,
                "https://insightbloom.example/api/users");

        useCase.execute("conf-1", "user-1");

        verify(emailPort).sendHtml(eq("alumno@example.com"), anyString(), anyString());
    }

    private static String eq(final String value) { return org.mockito.ArgumentMatchers.eq(value); }
}
