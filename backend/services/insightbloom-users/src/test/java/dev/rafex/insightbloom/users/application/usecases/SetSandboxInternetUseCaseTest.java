package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.Conference;
import dev.rafex.insightbloom.users.domain.ports.ConferenceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SetSandboxInternetUseCaseTest {
    private ConferenceRepository conferenceRepoMock;
    private SetSandboxInternetUseCase useCase;
    private Conference testConf;

    @BeforeEach
    void setup() {
        conferenceRepoMock = Mockito.mock(ConferenceRepository.class);
        useCase = new SetSandboxInternetUseCase(conferenceRepoMock);
        testConf = new Conference("conf1", "Test Conference", "user-org-1");
    }

    @Test
    void testEnableInternet() {
        Mockito.when(conferenceRepoMock.findByUuid("conf-1")).thenReturn(Optional.of(testConf));

        final var result = useCase.execute("conf-1", 1);

        assertEquals(1, result.getSandboxInternetEnabled());
        Mockito.verify(conferenceRepoMock).save(Mockito.any(Conference.class));
    }

    @Test
    void testDisableInternet() {
        testConf.setSandboxInternetEnabled(1); // Inicialmente habilitado
        Mockito.when(conferenceRepoMock.findByUuid("conf-1")).thenReturn(Optional.of(testConf));

        final var result = useCase.execute("conf-1", 0);

        assertEquals(0, result.getSandboxInternetEnabled());
        Mockito.verify(conferenceRepoMock).save(Mockito.any(Conference.class));
    }

    @Test
    void testConferenceNotFound() {
        Mockito.when(conferenceRepoMock.findByUuid("nonexistent"))
            .thenReturn(Optional.empty());

        final var ex = assertThrows(IllegalArgumentException.class,
            () -> useCase.execute("nonexistent", 1));
        assertEquals("conference_not_found", ex.getMessage());
    }

    @Test
    void testInvalidInternetEnabledValue() {
        Mockito.when(conferenceRepoMock.findByUuid("conf-1")).thenReturn(Optional.of(testConf));

        final var ex = assertThrows(IllegalArgumentException.class,
            () -> useCase.execute("conf-1", 2)); // invalid: not 0 or 1
        assertEquals("invalid_internet_enabled_value", ex.getMessage());
    }

    @Test
    void testToggleMultipleTimes() {
        Mockito.when(conferenceRepoMock.findByUuid("conf-1")).thenReturn(Optional.of(testConf));

        var result = useCase.execute("conf-1", 1);
        assertEquals(1, result.getSandboxInternetEnabled());

        result = useCase.execute("conf-1", 0);
        assertEquals(0, result.getSandboxInternetEnabled());

        result = useCase.execute("conf-1", 1);
        assertEquals(1, result.getSandboxInternetEnabled());

        Mockito.verify(conferenceRepoMock, Mockito.times(3)).save(Mockito.any(Conference.class));
    }
}
