package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.ports.SandboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PurgeSandboxPoolUseCaseTest {
    private SandboxRepository sandboxRepoMock;
    private PurgeSandboxPoolUseCase useCase;

    @BeforeEach
    void setup() {
        sandboxRepoMock = Mockito.mock(SandboxRepository.class);
        useCase = new PurgeSandboxPoolUseCase(sandboxRepoMock);
    }

    @Test
    void testPurgeExpiredSandboxes() {
        // Arrange
        final Instant now = Instant.now();
        Mockito.when(sandboxRepoMock.deleteExpired(now)).thenReturn(5);

        // Act
        final int deleted = useCase.execute(now);

        // Assert
        assertEquals(5, deleted);
        Mockito.verify(sandboxRepoMock).deleteExpired(now);
    }

    @Test
    void testPurgeNoExpiredSandboxes() {
        // Arrange
        final Instant now = Instant.now();
        Mockito.when(sandboxRepoMock.deleteExpired(now)).thenReturn(0);

        // Act
        final int deleted = useCase.execute(now);

        // Assert
        assertEquals(0, deleted);
        Mockito.verify(sandboxRepoMock).deleteExpired(now);
    }

    @Test
    void testPurgeLargeNumber() {
        // Arrange
        final Instant now = Instant.now();
        Mockito.when(sandboxRepoMock.deleteExpired(now)).thenReturn(150);

        // Act
        final int deleted = useCase.execute(now);

        // Assert
        assertEquals(150, deleted);
    }
}
