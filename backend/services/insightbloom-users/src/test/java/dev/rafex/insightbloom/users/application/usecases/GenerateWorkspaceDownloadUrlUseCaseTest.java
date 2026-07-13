package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.Sandbox;
import dev.rafex.insightbloom.users.domain.ports.SandboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class GenerateWorkspaceDownloadUrlUseCaseTest {
    private SandboxRepository sandboxRepoMock;
    private GenerateWorkspaceDownloadUrlUseCase useCase;
    private Sandbox testSandbox;

    @BeforeEach
    void setup() {
        sandboxRepoMock = Mockito.mock(SandboxRepository.class);
        useCase = new GenerateWorkspaceDownloadUrlUseCase(sandboxRepoMock, "https://ide.example.com");

        testSandbox = new Sandbox("conf-1", 0, "user-1", Instant.now().plusSeconds(3600));
    }

    @Test
    void testGenerateDownloadUrlSuccess() {
        // Arrange
        Mockito.when(sandboxRepoMock.findByConferenceAndUser("conf-1", "user-1"))
            .thenReturn(Optional.of(testSandbox));

        // Act
        final var result = useCase.execute("conf-1", "user-1");

        // Assert
        assertNotNull(result);
        assertEquals(testSandbox.getUuid(), result.sandboxUuid);
        assertTrue(result.downloadUrl.startsWith("https://ide.example.com/workspaces/"));
        assertTrue(result.downloadUrl.contains("?token="));
        assertEquals(3600, result.expiresInSeconds);
    }

    @Test
    void testGenerateDownloadUrlNoSandboxAssigned() {
        // Arrange
        Mockito.when(sandboxRepoMock.findByConferenceAndUser("conf-1", "user-1"))
            .thenReturn(Optional.empty());

        // Act & Assert
        final var ex = assertThrows(IllegalArgumentException.class,
            () -> useCase.execute("conf-1", "user-1"));
        assertEquals("sandbox_not_assigned", ex.getMessage());
    }

    @Test
    void testGenerateDownloadUrlTokenHasValidStructure() {
        // Arrange
        Mockito.when(sandboxRepoMock.findByConferenceAndUser("conf-1", "user-1"))
            .thenReturn(Optional.of(testSandbox));

        // Act
        final var result = useCase.execute("conf-1", "user-1");

        // Assert: token debe estar en URL y ser base64-ish (URL-safe)
        final String downloadUrl = result.downloadUrl;
        final int tokenIndex = downloadUrl.indexOf("token=");
        assertTrue(tokenIndex > 0);
        final String token = downloadUrl.substring(tokenIndex + 6);
        assertTrue(token.matches("[A-Za-z0-9_-]+"));
    }

    @Test
    void testMultipleDownloadsGenerateDifferentTokens() {
        // Arrange
        Mockito.when(sandboxRepoMock.findByConferenceAndUser("conf-1", "user-1"))
            .thenReturn(Optional.of(testSandbox));

        // Act
        final var result1 = useCase.execute("conf-1", "user-1");
        final var result2 = useCase.execute("conf-1", "user-1");

        // Assert: URLs deben ser distintas (por nonce)
        assertNotEquals(result1.downloadUrl, result2.downloadUrl);
    }
}
