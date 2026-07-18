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
        useCase = new SetSandboxConfigUseCase(repoMock, 50, 1136, 874);
        conf = new Conference("friendly1", "Test Conf", "user1");
        conf.setEventDate("2026-08-01");
    }

    @Test
    void testSetValidConfig() {
        Mockito.when(repoMock.findByUuid("conf1")).thenReturn(Optional.of(conf));

        var result = useCase.execute("conf1", "python", 10, "numpy pandas", "https://github.com/user/repo", 256, null, null);

        assertEquals("python", result.getSandboxVariant());
        assertEquals(10, result.getSandboxPoolSize());
        assertEquals("numpy pandas", result.getSandboxExtraPackages());
        assertEquals("https://github.com/user/repo", result.getSandboxRemoteGitUrl());
        assertEquals(256, result.getSandboxJvmHeapMb());
        Mockito.verify(repoMock).save(Mockito.any(Conference.class));
    }

    @Test
    void testPoolSizeExceedsMax() {
        Mockito.when(repoMock.findByUuid("conf1")).thenReturn(Optional.of(conf));

        var ex = assertThrows(IllegalArgumentException.class,
            () -> useCase.execute("conf1", "java", 100, null, null, null, null, null));
        assertEquals("pool_size_exceeds_platform_max", ex.getMessage());
    }

    @Test
    void testPoolSizeZero() {
        Mockito.when(repoMock.findByUuid("conf1")).thenReturn(Optional.of(conf));

        var ex = assertThrows(IllegalArgumentException.class,
            () -> useCase.execute("conf1", "web", 0, null, null, null, null, null));
        assertEquals("pool_size_must_be_positive", ex.getMessage());
    }

    @Test
    void testConferenceNotFound() {
        Mockito.when(repoMock.findByUuid("nonexistent")).thenReturn(Optional.empty());

        var ex = assertThrows(IllegalArgumentException.class,
            () -> useCase.execute("nonexistent", "python", 5, null, null, null, null, null));
        assertEquals("conference_not_found", ex.getMessage());
    }

    @Test
    void testNullPoolSizeAllowed() {
        Mockito.when(repoMock.findByUuid("conf1")).thenReturn(Optional.of(conf));

        var result = useCase.execute("conf1", "java", null, "gradle", null, null, null, null);

        assertNull(result.getSandboxPoolSize());
        assertEquals("java", result.getSandboxVariant());
        Mockito.verify(repoMock).save(Mockito.any(Conference.class));
    }

    @Test
    void testNullJvmHeapAllowed() {
        Mockito.when(repoMock.findByUuid("conf1")).thenReturn(Optional.of(conf));

        var result = useCase.execute("conf1", "python", 1, null, null, null, null, null);

        assertNull(result.getSandboxJvmHeapMb());
        Mockito.verify(repoMock).save(Mockito.any(Conference.class));
    }

    @Test
    void testJvmHeapTooSmallRejected() {
        Mockito.when(repoMock.findByUuid("conf1")).thenReturn(Optional.of(conf));

        var ex = assertThrows(IllegalArgumentException.class,
            () -> useCase.execute("conf1", "python", 1, null, null, 32, null, null));
        assertEquals("jvm_heap_too_small", ex.getMessage());
    }

    @Test
    void testJvmHeapExceedsDebianContainerLimitRejected() {
        Mockito.when(repoMock.findByUuid("conf1")).thenReturn(Optional.of(conf));

        // variant "python" (no terminal-nvim) usa el techo Debian (1136 en este test)
        var ex = assertThrows(IllegalArgumentException.class,
            () -> useCase.execute("conf1", "python", 1, null, null, 2000, null, null));
        assertEquals("jvm_heap_exceeds_container_limit", ex.getMessage());
    }

    @Test
    void testJvmHeapExceedsNeovimContainerLimitRejected() {
        Mockito.when(repoMock.findByUuid("conf1")).thenReturn(Optional.of(conf));

        // variant "terminal-nvim" usa el techo Alpine, mas chico (874 en este test) -- el mismo
        // valor de heap (900) es valido para Debian pero invalido para Neovim, confirma que la
        // validacion mira el techo correcto segun la variante.
        var ex = assertThrows(IllegalArgumentException.class,
            () -> useCase.execute("conf1", "terminal-nvim", 1, null, null, 900, null, null));
        assertEquals("jvm_heap_exceeds_container_limit", ex.getMessage());
    }

    @Test
    void testNullSeatsPerPodAllowed() {
        Mockito.when(repoMock.findByUuid("conf1")).thenReturn(Optional.of(conf));

        var result = useCase.execute("conf1", "terminal-nvim", 1, null, null, null, null, null);

        assertNull(result.getSandboxSeatsPerPod());
        Mockito.verify(repoMock).save(Mockito.any(Conference.class));
    }

    @Test
    void testValidSeatsPerPod() {
        Mockito.when(repoMock.findByUuid("conf1")).thenReturn(Optional.of(conf));

        var result = useCase.execute("conf1", "terminal-nvim", 1, null, null, null, 6, null);

        assertEquals(6, result.getSandboxSeatsPerPod());
        Mockito.verify(repoMock).save(Mockito.any(Conference.class));
    }

    @Test
    void testSeatsPerPodTooLowRejected() {
        Mockito.when(repoMock.findByUuid("conf1")).thenReturn(Optional.of(conf));

        var ex = assertThrows(IllegalArgumentException.class,
            () -> useCase.execute("conf1", "terminal-nvim", 1, null, null, null, 0, null));
        assertEquals("seats_per_pod_out_of_range", ex.getMessage());
    }

    @Test
    void testSeatsPerPodTooHighRejected() {
        Mockito.when(repoMock.findByUuid("conf1")).thenReturn(Optional.of(conf));

        var ex = assertThrows(IllegalArgumentException.class,
            () -> useCase.execute("conf1", "terminal-nvim", 1, null, null, null, 11, null));
        assertEquals("seats_per_pod_out_of_range", ex.getMessage());
    }
}
