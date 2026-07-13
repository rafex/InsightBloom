package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.Sandbox;
import dev.rafex.insightbloom.users.domain.ports.SandboxOrchestrator;
import dev.rafex.insightbloom.users.domain.ports.SandboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PurgeSandboxPoolUseCaseTest {
    private SandboxRepository sandboxRepoMock;
    private SandboxOrchestrator orchestratorMock;
    private PurgeSandboxPoolUseCase useCase;

    @BeforeEach
    void setup() {
        sandboxRepoMock = Mockito.mock(SandboxRepository.class);
        orchestratorMock = Mockito.mock(SandboxOrchestrator.class);
        useCase = new PurgeSandboxPoolUseCase(sandboxRepoMock, orchestratorMock);
    }

    @Test
    void testPurgeDeletesPodsBeforeDbRows() {
        final Instant now = Instant.now();
        final var expired1 = new Sandbox("conf-1", 0, "user-a", now.minusSeconds(10));
        final var expired2 = new Sandbox("conf-2", 0, "user-b", now.minusSeconds(20));
        Mockito.when(sandboxRepoMock.findExpired(now)).thenReturn(List.of(expired1, expired2));
        Mockito.when(sandboxRepoMock.deleteExpired(now)).thenReturn(2);

        final int deleted = useCase.execute(now);

        assertEquals(2, deleted);
        Mockito.verify(orchestratorMock).deleteSandbox(expired1.podName());
        Mockito.verify(orchestratorMock).deleteSandbox(expired2.podName());
        Mockito.verify(sandboxRepoMock).deleteExpired(now);
    }

    @Test
    void testPurgeContinuesWhenOneDeleteFails() {
        final Instant now = Instant.now();
        final var expired1 = new Sandbox("conf-1", 0, "user-a", now.minusSeconds(10));
        final var expired2 = new Sandbox("conf-2", 0, "user-b", now.minusSeconds(20));
        Mockito.when(sandboxRepoMock.findExpired(now)).thenReturn(List.of(expired1, expired2));
        Mockito.doThrow(new RuntimeException("k8s unreachable"))
            .when(orchestratorMock).deleteSandbox(expired1.podName());
        Mockito.when(sandboxRepoMock.deleteExpired(now)).thenReturn(2);

        final int deleted = useCase.execute(now);

        assertEquals(2, deleted);
        Mockito.verify(orchestratorMock).deleteSandbox(expired2.podName());
        Mockito.verify(sandboxRepoMock).deleteExpired(now);
    }

    @Test
    void testPurgeNoExpiredSandboxes() {
        final Instant now = Instant.now();
        Mockito.when(sandboxRepoMock.findExpired(now)).thenReturn(List.of());
        Mockito.when(sandboxRepoMock.deleteExpired(now)).thenReturn(0);

        final int deleted = useCase.execute(now);

        assertEquals(0, deleted);
        Mockito.verifyNoInteractions(orchestratorMock);
    }
}
