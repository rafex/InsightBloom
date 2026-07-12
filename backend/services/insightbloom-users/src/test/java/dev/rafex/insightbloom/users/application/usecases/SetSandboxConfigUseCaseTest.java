package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.Conference;
import dev.rafex.insightbloom.users.domain.ports.ConferenceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SetSandboxConfigUseCaseTest {
    private ConferenceRepository repoMock;
    private SetSandboxConfigUseCase useCase;
    private Conference conf;

    @BeforeEach
    void setup() {
        repoMock = Mockito.mock(ConferenceRepository.class);
        useCase = new SetSandboxConfigUseCase(repoMock, 50);
        conf = new Conference("friendly1", "Test Conf", "user1");
        conf.setEventDate("2026-08-01");
    }

    @Test
    void testSetValidConfig() {
        Mockito.when(repoMock.findByUuid("conf1")).thenReturn(Optional.of(conf));

        var result = useCase.execute("conf1", "python", 10, "numpy pandas", "https://github.com/user/repo");

        assertEquals("python", result.getSandboxVariant());
        assertEquals(10, result.getSandboxPoolSize());
        assertEquals("numpy pandas", result.getSandboxExtraPackages());
        assertEquals("https://github.com/user/repo", result.getSandboxRemoteGitUrl());
        Mockito.verify(repoMock).save(Mockito.any(Conference.class));
    }

    @Test
    void testPoolSizeExceedsMax() {
        Mockito.when(repoMock.findByUuid("conf1")).thenReturn(Optional.of(conf));

        var ex = assertThrows(IllegalArgumentException.class,
            () -> useCase.execute("conf1", "java", 100, null, null));
        assertEquals("pool_size_exceeds_platform_max", ex.getMessage());
    }

    @Test
    void testPoolSizeZero() {
        Mockito.when(repoMock.findByUuid("conf1")).thenReturn(Optional.of(conf));

        var ex = assertThrows(IllegalArgumentException.class,
            () -> useCase.execute("conf1", "web", 0, null, null));
        assertEquals("pool_size_must_be_positive", ex.getMessage());
    }

    @Test
    void testConferenceNotFound() {
        Mockito.when(repoMock.findByUuid("nonexistent")).thenReturn(Optional.empty());

        var ex = assertThrows(IllegalArgumentException.class,
            () -> useCase.execute("nonexistent", "python", 5, null, null));
        assertEquals("conference_not_found", ex.getMessage());
    }

    @Test
    void testNullPoolSizeAllowed() {
        Mockito.when(repoMock.findByUuid("conf1")).thenReturn(Optional.of(conf));

        var result = useCase.execute("conf1", "java", null, "gradle", null);

        assertNull(result.getSandboxPoolSize());
        assertEquals("java", result.getSandboxVariant());
        Mockito.verify(repoMock).save(Mockito.any(Conference.class));
    }
}
