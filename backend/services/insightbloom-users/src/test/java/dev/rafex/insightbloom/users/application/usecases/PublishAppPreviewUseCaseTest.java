package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.Sandbox;
import dev.rafex.insightbloom.users.domain.model.SandboxAppPreview;
import dev.rafex.insightbloom.users.domain.ports.SandboxAppPreviewRepository;
import dev.rafex.insightbloom.users.domain.ports.SandboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class PublishAppPreviewUseCaseTest {

    private SandboxRepository sandboxRepository;
    private SandboxAppPreviewRepository previewRepository;
    private PublishAppPreviewUseCase useCase;

    @BeforeEach
    void setUp() {
        sandboxRepository = Mockito.mock(SandboxRepository.class);
        previewRepository = Mockito.mock(SandboxAppPreviewRepository.class);
        Mockito.when(previewRepository.save(Mockito.any())).thenAnswer(inv -> inv.getArgument(0));
        useCase = new PublishAppPreviewUseCase(sandboxRepository, previewRepository, 9000);
    }

    private static Sandbox activeSandbox(final int seatIndex) {
        return new Sandbox("conf-1", 0, seatIndex, Sandbox.VARIANT_CLI, "user-1",
                Instant.now().plusSeconds(3600));
    }

    @Test
    void throwsWhenSandboxNotAssigned() {
        Mockito.when(sandboxRepository.findByConferenceAndUser("conf-1", "user-1")).thenReturn(Optional.empty());

        final var e = assertThrows(IllegalArgumentException.class, () -> useCase.execute("conf-1", "user-1", 3600));
        assertEquals("sandbox_not_assigned", e.getMessage());
    }

    @Test
    void throwsWhenSandboxExpired() {
        final Sandbox expired = new Sandbox("conf-1", 0, 0, Sandbox.VARIANT_WEB, "user-1",
                Instant.now().minusSeconds(10));
        Mockito.when(sandboxRepository.findByConferenceAndUser("conf-1", "user-1")).thenReturn(Optional.of(expired));

        final var e = assertThrows(IllegalArgumentException.class, () -> useCase.execute("conf-1", "user-1", 3600));
        assertEquals("sandbox_expired", e.getMessage());
    }

    @Test
    void computesTargetPortFromSeatIndexAndAppBasePort() {
        Mockito.when(sandboxRepository.findByConferenceAndUser("conf-1", "user-1"))
                .thenReturn(Optional.of(activeSandbox(3)));

        final SandboxAppPreview preview = useCase.execute("conf-1", "user-1", 1800);

        assertEquals(9003, preview.targetPort());
        assertEquals("conf-1", preview.conferenceUuid());
        assertEquals("user-1", preview.userUuid());
        assertNotNull(preview.accessToken());
        assertFalse(preview.accessToken().isBlank());
        assertTrue(preview.expiresAt().isAfter(Instant.now().plusSeconds(1700)));
        Mockito.verify(previewRepository).save(preview);
    }

    @Test
    void generatesDifferentTokensAcrossPublications() {
        Mockito.when(sandboxRepository.findByConferenceAndUser("conf-1", "user-1"))
                .thenReturn(Optional.of(activeSandbox(0)));

        final SandboxAppPreview first = useCase.execute("conf-1", "user-1", 3600);
        final SandboxAppPreview second = useCase.execute("conf-1", "user-1", 3600);

        assertNotEquals(first.accessToken(), second.accessToken());
        assertNotEquals(first.uuid(), second.uuid());
    }
}
