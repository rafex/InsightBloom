package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.Sandbox;
import dev.rafex.insightbloom.users.domain.ports.SandboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ResolveSandboxTargetUseCaseTest {
    private ValidateTokenUseCase validateTokenUseCaseMock;
    private SandboxRepository sandboxRepoMock;
    private ResolveSandboxTargetUseCase useCase;

    @BeforeEach
    void setup() {
        validateTokenUseCaseMock = Mockito.mock(ValidateTokenUseCase.class);
        sandboxRepoMock = Mockito.mock(SandboxRepository.class);
        useCase = new ResolveSandboxTargetUseCase(
                validateTokenUseCaseMock, sandboxRepoMock, "insightbloom-sandboxes", 8080);
    }

    @Test
    void testResolvesTargetForValidTokenWithSandbox() {
        Mockito.when(validateTokenUseCaseMock.execute("tok"))
                .thenReturn(new ValidateTokenUseCase.ValidationResult(true, "user-1", "user", "organizer", null));
        final var sandbox = new Sandbox("conf-1", 0, "user-1", Instant.now().plusSeconds(3600));
        Mockito.when(sandboxRepoMock.findByConferenceAndUser("conf-1", "user-1"))
                .thenReturn(Optional.of(sandbox));

        final var result = useCase.execute("tok", "conf-1");

        assertTrue(result.isPresent());
        assertEquals("http://" + sandbox.podName() + "-svc.insightbloom-sandboxes.svc.cluster.local:8080", result.get());
    }

    @Test
    void testInvalidTokenReturnsEmpty() {
        Mockito.when(validateTokenUseCaseMock.execute("bad"))
                .thenReturn(new ValidateTokenUseCase.ValidationResult(false, null, null, null, null));

        final var result = useCase.execute("bad", "conf-1");

        assertTrue(result.isEmpty());
        Mockito.verifyNoInteractions(sandboxRepoMock);
    }

    @Test
    void testResolvesSeatPortForSharedPod() {
        Mockito.when(validateTokenUseCaseMock.execute("tok"))
                .thenReturn(new ValidateTokenUseCase.ValidationResult(true, "user-2", "user", "organizer", null));
        // Asiento 2 de un Pod compartido -- debe resolver a basePort+2, no al puerto fijo de siempre.
        final var sandbox = new Sandbox("conf-1", 0, 2, "user-2", Instant.now().plusSeconds(3600));
        Mockito.when(sandboxRepoMock.findByConferenceAndUser("conf-1", "user-2"))
                .thenReturn(Optional.of(sandbox));

        final var result = useCase.execute("tok", "conf-1");

        assertTrue(result.isPresent());
        assertEquals("http://" + sandbox.podName() + "-svc.insightbloom-sandboxes.svc.cluster.local:8082", result.get());
    }

    @Test
    void testNoSandboxAssignedReturnsEmpty() {
        Mockito.when(validateTokenUseCaseMock.execute("tok"))
                .thenReturn(new ValidateTokenUseCase.ValidationResult(true, "user-1", "user", "guest", null));
        Mockito.when(sandboxRepoMock.findByConferenceAndUser("conf-1", "user-1"))
                .thenReturn(Optional.empty());

        final var result = useCase.execute("tok", "conf-1");

        assertTrue(result.isEmpty());
    }
}
