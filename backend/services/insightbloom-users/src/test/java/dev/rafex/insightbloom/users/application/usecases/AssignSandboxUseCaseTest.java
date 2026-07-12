package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.Conference;
import dev.rafex.insightbloom.users.domain.model.Sandbox;
import dev.rafex.insightbloom.users.domain.ports.ConferenceRepository;
import dev.rafex.insightbloom.users.domain.ports.SandboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class AssignSandboxUseCaseTest {
    private SandboxRepository sandboxRepoMock;
    private ConferenceRepository conferenceRepoMock;
    private AssignSandboxUseCase useCase;
    private Conference testConf;

    @BeforeEach
    void setup() {
        sandboxRepoMock = Mockito.mock(SandboxRepository.class);
        conferenceRepoMock = Mockito.mock(ConferenceRepository.class);
        useCase = new AssignSandboxUseCase(sandboxRepoMock, conferenceRepoMock);
        testConf = new Conference("test1", "Test Conference", "user-org-1");
    }

    @Test
    void testAssignSandboxSuccess() {
        // Arrange: evento existe, hay un slot libre
        Mockito.when(conferenceRepoMock.findByUuid("conf-1")).thenReturn(Optional.of(testConf));
        final var unassignedSlot = new Sandbox("conf-1", 2, Instant.now().plusSeconds(3600));
        Mockito.when(sandboxRepoMock.findUnassignedSlotForConference("conf-1"))
            .thenReturn(Optional.of(unassignedSlot));

        // Act
        final var result = useCase.execute("conf-1", "user-student-1");

        // Assert
        assertNotNull(result);
        assertEquals("user-student-1", result.getUserUuid());
        assertTrue(result.isAssigned());
        assertNotNull(result.getAssignedAt());
        Mockito.verify(sandboxRepoMock).save(Mockito.any(Sandbox.class));
    }

    @Test
    void testAssignSandboxConferenceNotFound() {
        // Arrange
        Mockito.when(conferenceRepoMock.findByUuid("nonexistent"))
            .thenReturn(Optional.empty());

        // Act & Assert
        final var ex = assertThrows(IllegalArgumentException.class,
            () -> useCase.execute("nonexistent", "user-student-1"));
        assertEquals("conference_not_found", ex.getMessage());
    }

    @Test
    void testAssignSandboxPoolFull() {
        // Arrange: evento existe pero todos los slots están asignados
        Mockito.when(conferenceRepoMock.findByUuid("conf-1")).thenReturn(Optional.of(testConf));
        Mockito.when(sandboxRepoMock.findUnassignedSlotForConference("conf-1"))
            .thenReturn(Optional.empty());

        // Act & Assert
        final var ex = assertThrows(IllegalArgumentException.class,
            () -> useCase.execute("conf-1", "user-student-1"));
        assertEquals("sandbox_pool_full", ex.getMessage());
    }

    @Test
    void testAssignSandboxConcurrencyCollision() {
        // Arrange: slot libre inicialmente, pero UNIQUE constraint falla al guardar
        // (simula que otro user asignó el mismo slot justo antes)
        Mockito.when(conferenceRepoMock.findByUuid("conf-1")).thenReturn(Optional.of(testConf));
        final var slot = new Sandbox("conf-1", 1, Instant.now().plusSeconds(3600));
        Mockito.when(sandboxRepoMock.findUnassignedSlotForConference("conf-1"))
            .thenReturn(Optional.of(slot));
        Mockito.doThrow(new RuntimeException("UNIQUE constraint failed"))
            .when(sandboxRepoMock).save(Mockito.any());

        // Act & Assert
        final var ex = assertThrows(IllegalArgumentException.class,
            () -> useCase.execute("conf-1", "user-student-1"));
        assertEquals("sandbox_pool_full", ex.getMessage());
    }

    @Test
    void testAssignSandboxToMultipleUsersIndependent() {
        // Arrange: dos usuarios, dos slots diferentes
        Mockito.when(conferenceRepoMock.findByUuid("conf-1")).thenReturn(Optional.of(testConf));
        final var slot1 = new Sandbox("conf-1", 0, Instant.now().plusSeconds(3600));
        final var slot2 = new Sandbox("conf-1", 1, Instant.now().plusSeconds(3600));

        // Primera llamada: devuelve slot 0
        Mockito.when(sandboxRepoMock.findUnassignedSlotForConference("conf-1"))
            .thenReturn(Optional.of(slot1))
            .thenReturn(Optional.of(slot2));

        // Act
        final var result1 = useCase.execute("conf-1", "user-1");
        final var result2 = useCase.execute("conf-1", "user-2");

        // Assert
        assertEquals("user-1", result1.getUserUuid());
        assertEquals("user-2", result2.getUserUuid());
        assertEquals(0, result1.getSandboxSlot());
        assertEquals(1, result2.getSandboxSlot());
    }
}
