package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.Sandbox;
import dev.rafex.insightbloom.users.domain.model.WorkspaceFileContent;
import dev.rafex.insightbloom.users.domain.model.WorkspaceFileEntry;
import dev.rafex.insightbloom.users.domain.ports.SandboxOrchestrator;
import dev.rafex.insightbloom.users.domain.ports.SandboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class WorkspaceFileUseCasesTest {
    private SandboxRepository sandboxRepoMock;
    private SandboxOrchestrator orchestratorMock;
    private Sandbox sandbox;

    @BeforeEach
    void setup() {
        sandboxRepoMock = Mockito.mock(SandboxRepository.class);
        orchestratorMock = Mockito.mock(SandboxOrchestrator.class);
        sandbox = new Sandbox("conf-1", 0, "user-1", Instant.now().plusSeconds(3600));
        Mockito.when(sandboxRepoMock.findByConferenceAndUser("conf-1", "user-1")).thenReturn(Optional.of(sandbox));
        Mockito.when(sandboxRepoMock.findByConferenceAndUser("conf-1", "no-sandbox")).thenReturn(Optional.empty());
    }

    @Test
    void testListWorkspaceFilesDelegatesToOrchestratorWithSandboxPodAndSeat() {
        final var entries = List.of(new WorkspaceFileEntry("Main.java", false, 123.0, 20));
        Mockito.when(orchestratorMock.listWorkspaceFiles(sandbox.podName(), sandbox.getSeatIndex(), "src"))
            .thenReturn(entries);
        final var useCase = new ListWorkspaceFilesUseCase(sandboxRepoMock, orchestratorMock);

        final var result = useCase.execute("conf-1", "user-1", "src");

        assertEquals(entries, result);
    }

    @Test
    void testListWorkspaceFilesThrowsWhenNoSandboxAssigned() {
        final var useCase = new ListWorkspaceFilesUseCase(sandboxRepoMock, orchestratorMock);
        final var ex = assertThrows(IllegalArgumentException.class,
            () -> useCase.execute("conf-1", "no-sandbox", ""));
        assertEquals("sandbox_not_assigned", ex.getMessage());
    }

    @Test
    void testReadWorkspaceFileDelegatesToOrchestrator() {
        final var content = new WorkspaceFileContent("class Main {}", 123.0);
        Mockito.when(orchestratorMock.readWorkspaceFile(sandbox.podName(), sandbox.getSeatIndex(), "Main.java"))
            .thenReturn(content);
        final var useCase = new ReadWorkspaceFileUseCase(sandboxRepoMock, orchestratorMock);

        final var result = useCase.execute("conf-1", "user-1", "Main.java");

        assertEquals(content, result);
    }

    @Test
    void testWriteWorkspaceFileDelegatesToOrchestratorWithExpectedMtime() {
        Mockito.when(orchestratorMock.writeWorkspaceFile(sandbox.podName(), sandbox.getSeatIndex(), "Main.java", "x", 123.0))
            .thenReturn(456.0);
        final var useCase = new WriteWorkspaceFileUseCase(sandboxRepoMock, orchestratorMock);

        final var result = useCase.execute("conf-1", "user-1", "Main.java", "x", 123.0);

        assertEquals(456.0, result);
    }

    @Test
    void testWriteWorkspaceFilePropagatesConflict() {
        Mockito.when(orchestratorMock.writeWorkspaceFile(Mockito.anyString(), Mockito.anyInt(), Mockito.anyString(),
                Mockito.anyString(), Mockito.any()))
            .thenThrow(new IllegalArgumentException("file_conflict"));
        final var useCase = new WriteWorkspaceFileUseCase(sandboxRepoMock, orchestratorMock);

        final var ex = assertThrows(IllegalArgumentException.class,
            () -> useCase.execute("conf-1", "user-1", "Main.java", "x", 123.0));
        assertEquals("file_conflict", ex.getMessage());
    }
}
