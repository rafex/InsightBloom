package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.Conference;
import dev.rafex.insightbloom.users.domain.model.Sandbox;
import dev.rafex.insightbloom.users.domain.ports.ConferenceRepository;
import dev.rafex.insightbloom.users.domain.ports.SandboxOrchestrator;
import dev.rafex.insightbloom.users.domain.ports.SandboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class EnsureUnassignedSandboxUseCaseTest {
    private SandboxRepository sandboxRepoMock;
    private ConferenceRepository conferenceRepoMock;
    private SandboxOrchestrator orchestratorMock;
    private EnsureUnassignedSandboxUseCase useCase;
    private Conference testConf;

    @BeforeEach
    void setup() {
        sandboxRepoMock = Mockito.mock(SandboxRepository.class);
        conferenceRepoMock = Mockito.mock(ConferenceRepository.class);
        orchestratorMock = Mockito.mock(SandboxOrchestrator.class);
        useCase = new EnsureUnassignedSandboxUseCase(sandboxRepoMock, conferenceRepoMock, orchestratorMock, 3600);
        testConf = new Conference("test1", "Test Conference", "user-org-1");
        testConf.setSandboxPoolSize(2);
    }

    @Test
    void testCreatesUnassignedPodWhenConferenceHasNone() {
        Mockito.when(conferenceRepoMock.findByUuid("conf-1")).thenReturn(Optional.of(testConf));
        Mockito.when(sandboxRepoMock.findByConferenceUuid("conf-1")).thenReturn(List.of());

        useCase.execute("conf-1");

        Mockito.verify(orchestratorMock).createSandbox(
            Mockito.eq(Sandbox.podName("conf-1", 0)), Mockito.eq("conf-1"), Mockito.eq("python"),
            Mockito.isNull(), Mockito.isNull(), Mockito.eq(false), Mockito.isNull(), Mockito.isNull());
        final var captor = org.mockito.ArgumentCaptor.forClass(Sandbox.class);
        Mockito.verify(sandboxRepoMock).save(captor.capture());
        assertNull(captor.getValue().getUserUuid());
        assertNull(captor.getValue().getAssignedAt());
    }

    @Test
    void testNoOpWhenConferenceAlreadyHasActiveSandbox() {
        Mockito.when(conferenceRepoMock.findByUuid("conf-1")).thenReturn(Optional.of(testConf));
        final var existing = new Sandbox("conf-1", 0, "user-a", java.time.Instant.now().plusSeconds(3600));
        Mockito.when(sandboxRepoMock.findByConferenceUuid("conf-1")).thenReturn(List.of(existing));

        useCase.execute("conf-1");

        Mockito.verifyNoInteractions(orchestratorMock);
        Mockito.verify(sandboxRepoMock, Mockito.never()).save(Mockito.any());
    }

    @Test
    void testSwallowsKubernetesNotConfigured() {
        Mockito.when(conferenceRepoMock.findByUuid("conf-1")).thenReturn(Optional.of(testConf));
        Mockito.when(sandboxRepoMock.findByConferenceUuid("conf-1")).thenReturn(List.of());
        Mockito.doThrow(new IllegalStateException("kubernetes_not_configured"))
            .when(orchestratorMock).createSandbox(Mockito.anyString(), Mockito.anyString(), Mockito.anyString(),
                Mockito.any(), Mockito.any(), Mockito.anyBoolean(), Mockito.any(), Mockito.any());

        assertDoesNotThrow(() -> useCase.execute("conf-1"));
        Mockito.verify(sandboxRepoMock, Mockito.never()).save(Mockito.any());
    }

    @Test
    void testRollsBackPodOnConcurrentPreProvisionRace() {
        Mockito.when(conferenceRepoMock.findByUuid("conf-1")).thenReturn(Optional.of(testConf));
        Mockito.when(sandboxRepoMock.findByConferenceUuid("conf-1")).thenReturn(List.of());
        Mockito.doThrow(new RuntimeException("UNIQUE constraint failed"))
            .when(sandboxRepoMock).save(Mockito.any());

        assertDoesNotThrow(() -> useCase.execute("conf-1"));
        Mockito.verify(orchestratorMock).deleteSandbox(Mockito.anyString());
    }

    @Test
    void testConferenceNotFound() {
        Mockito.when(conferenceRepoMock.findByUuid("nonexistent")).thenReturn(Optional.empty());

        final var ex = assertThrows(IllegalArgumentException.class, () -> useCase.execute("nonexistent"));
        assertEquals("conference_not_found", ex.getMessage());
    }
}
