package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.Conference;
import dev.rafex.insightbloom.users.domain.ports.ConferenceRepository;
import dev.rafex.insightbloom.users.domain.ports.SandboxOrchestrator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SetSandboxInternetUseCaseTest {
    private ConferenceRepository conferenceRepoMock;
    private SandboxOrchestrator orchestratorMock;
    private SetSandboxInternetUseCase useCase;
    private Conference testConf;

    @BeforeEach
    void setup() {
        conferenceRepoMock = Mockito.mock(ConferenceRepository.class);
        orchestratorMock = Mockito.mock(SandboxOrchestrator.class);
        useCase = new SetSandboxInternetUseCase(conferenceRepoMock, orchestratorMock);
        testConf = new Conference("conf1", "Test Conference", "user-org-1");
    }

    @Test
    void testEnableInternet() {
        // Arrange
        Mockito.when(conferenceRepoMock.findByUuid("conf-1")).thenReturn(Optional.of(testConf));

        // Act
        final var result = useCase.execute("conf-1", 1);

        // Assert
        assertEquals(1, result.getSandboxInternetEnabled());
        Mockito.verify(conferenceRepoMock).save(Mockito.any(Conference.class));
        Mockito.verify(orchestratorMock).allowInternetEgress(Mockito.anyString());
    }

    @Test
    void testDisableInternet() {
        // Arrange
        testConf.setSandboxInternetEnabled(1); // Inicialmente habilitado
        Mockito.when(conferenceRepoMock.findByUuid("conf-1")).thenReturn(Optional.of(testConf));

        // Act
        final var result = useCase.execute("conf-1", 0);

        // Assert
        assertEquals(0, result.getSandboxInternetEnabled());
        Mockito.verify(conferenceRepoMock).save(Mockito.any(Conference.class));
        Mockito.verify(orchestratorMock).denyInternetEgress(Mockito.anyString());
    }

    @Test
    void testKubernetesNotConfiguredStillSavesFlag() {
        Mockito.when(conferenceRepoMock.findByUuid("conf-1")).thenReturn(Optional.of(testConf));
        Mockito.doThrow(new IllegalStateException("kubernetes_not_configured"))
            .when(orchestratorMock).allowInternetEgress(Mockito.anyString());

        final var result = useCase.execute("conf-1", 1);

        assertEquals(1, result.getSandboxInternetEnabled());
        Mockito.verify(conferenceRepoMock).save(Mockito.any(Conference.class));
    }

    @Test
    void testConferenceNotFound() {
        // Arrange
        Mockito.when(conferenceRepoMock.findByUuid("nonexistent"))
            .thenReturn(Optional.empty());

        // Act & Assert
        final var ex = assertThrows(IllegalArgumentException.class,
            () -> useCase.execute("nonexistent", 1));
        assertEquals("conference_not_found", ex.getMessage());
    }

    @Test
    void testInvalidInternetEnabledValue() {
        // Arrange
        Mockito.when(conferenceRepoMock.findByUuid("conf-1")).thenReturn(Optional.of(testConf));

        // Act & Assert
        final var ex = assertThrows(IllegalArgumentException.class,
            () -> useCase.execute("conf-1", 2)); // invalid: not 0 or 1
        assertEquals("invalid_internet_enabled_value", ex.getMessage());
    }

    @Test
    void testToggleMultipleTimes() {
        // Arrange
        Mockito.when(conferenceRepoMock.findByUuid("conf-1")).thenReturn(Optional.of(testConf));

        // Act
        var result = useCase.execute("conf-1", 1);
        assertEquals(1, result.getSandboxInternetEnabled());

        result = useCase.execute("conf-1", 0);
        assertEquals(0, result.getSandboxInternetEnabled());

        result = useCase.execute("conf-1", 1);
        assertEquals(1, result.getSandboxInternetEnabled());

        // Assert: save llamado 3 veces
        Mockito.verify(conferenceRepoMock, Mockito.times(3)).save(Mockito.any(Conference.class));
    }
}
