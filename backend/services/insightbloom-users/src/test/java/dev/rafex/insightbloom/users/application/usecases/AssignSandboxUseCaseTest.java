package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.Conference;
import dev.rafex.insightbloom.users.domain.model.Sandbox;
import dev.rafex.insightbloom.users.domain.ports.ConferenceRepository;
import dev.rafex.insightbloom.users.domain.ports.SandboxOrchestrator;
import dev.rafex.insightbloom.users.domain.ports.SandboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class AssignSandboxUseCaseTest {
    private SandboxRepository sandboxRepoMock;
    private ConferenceRepository conferenceRepoMock;
    private SandboxOrchestrator orchestratorMock;
    private AssignSandboxUseCase useCase;
    private Conference testConf;

    @BeforeEach
    void setup() {
        sandboxRepoMock = Mockito.mock(SandboxRepository.class);
        conferenceRepoMock = Mockito.mock(ConferenceRepository.class);
        orchestratorMock = Mockito.mock(SandboxOrchestrator.class);
        useCase = new AssignSandboxUseCase(sandboxRepoMock, conferenceRepoMock, orchestratorMock, 3600);
        testConf = new Conference("test1", "Test Conference", "user-org-1");
        testConf.setSandboxPoolSize(2);
    }

    @Test
    void testAssignSandboxCreatesPodAndSaves() {
        Mockito.when(conferenceRepoMock.findByUuid("conf-1")).thenReturn(Optional.of(testConf));
        Mockito.when(sandboxRepoMock.findByConferenceAndUser("conf-1", "user-student-1")).thenReturn(Optional.empty());
        Mockito.when(sandboxRepoMock.findByConferenceUuid("conf-1")).thenReturn(List.of());

        final var result = useCase.execute("conf-1", "user-student-1");

        assertNotNull(result);
        assertEquals("user-student-1", result.getUserUuid());
        assertEquals(0, result.getSandboxSlot());
        Mockito.verify(orchestratorMock).createSandbox(
                Mockito.eq(result.podName()), Mockito.eq("conf-1"), Mockito.eq("python"),
                Mockito.isNull(), Mockito.isNull(), Mockito.eq(false));
        Mockito.verify(sandboxRepoMock).save(Mockito.any(Sandbox.class));
    }

    @Test
    void testAssignSandboxReusesExistingWhenPodStillAlive() {
        Mockito.when(conferenceRepoMock.findByUuid("conf-1")).thenReturn(Optional.of(testConf));
        final var existing = new Sandbox("conf-1", 0, "user-student-1", java.time.Instant.now().plusSeconds(3600));
        Mockito.when(sandboxRepoMock.findByConferenceAndUser("conf-1", "user-student-1")).thenReturn(Optional.of(existing));
        Mockito.when(orchestratorMock.getPhase(existing.podName())).thenReturn("Running");

        final var result = useCase.execute("conf-1", "user-student-1");

        assertSame(existing, result);
        Mockito.verify(orchestratorMock, Mockito.never()).createSandbox(
            Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.any(), Mockito.any(), Mockito.anyBoolean());
        Mockito.verify(sandboxRepoMock, Mockito.never()).save(Mockito.any());
    }

    @Test
    void testAssignSandboxRecreatesPodWhenExistingAssignmentPointsToDeadPod() {
        // Reproduce el incidente 2026-07-16: la fila sobrevive a un pod borrado a mano/evicted --
        // sin este chequeo, GetSandbox devolveria PENDING para siempre.
        Mockito.when(conferenceRepoMock.findByUuid("conf-1")).thenReturn(Optional.of(testConf));
        final var existing = new Sandbox("conf-1", 0, "user-student-1", java.time.Instant.now().plusSeconds(3600));
        Mockito.when(sandboxRepoMock.findByConferenceAndUser("conf-1", "user-student-1")).thenReturn(Optional.of(existing));
        Mockito.when(orchestratorMock.getPhase(existing.podName())).thenReturn(null);

        final var result = useCase.execute("conf-1", "user-student-1");

        assertSame(existing, result);
        Mockito.verify(orchestratorMock).createSandbox(
            Mockito.eq(existing.podName()), Mockito.eq("conf-1"), Mockito.eq("python"),
            Mockito.isNull(), Mockito.isNull(), Mockito.eq(false));
        Mockito.verify(sandboxRepoMock, Mockito.never()).save(Mockito.any());
    }

    @Test
    void testAssignSandboxConferenceNotFound() {
        Mockito.when(conferenceRepoMock.findByUuid("nonexistent")).thenReturn(Optional.empty());

        final var ex = assertThrows(IllegalArgumentException.class,
            () -> useCase.execute("nonexistent", "user-student-1"));
        assertEquals("conference_not_found", ex.getMessage());
    }

    @Test
    void testAssignSandboxPoolFull() {
        Mockito.when(conferenceRepoMock.findByUuid("conf-1")).thenReturn(Optional.of(testConf));
        Mockito.when(sandboxRepoMock.findByConferenceAndUser("conf-1", "user-student-1")).thenReturn(Optional.empty());
        final var slot0 = new Sandbox("conf-1", 0, "user-a", java.time.Instant.now().plusSeconds(3600));
        final var slot1 = new Sandbox("conf-1", 1, "user-b", java.time.Instant.now().plusSeconds(3600));
        Mockito.when(sandboxRepoMock.findByConferenceUuid("conf-1")).thenReturn(List.of(slot0, slot1));

        final var ex = assertThrows(IllegalArgumentException.class,
            () -> useCase.execute("conf-1", "user-student-1"));
        assertEquals("sandbox_pool_full", ex.getMessage());
        Mockito.verifyNoInteractions(orchestratorMock);
    }

    @Test
    void testAssignSandboxConcurrencyCollisionRollsBackPod() {
        // Slot libre segun el conteo, pero el INSERT falla (otro request gano la carrera)
        Mockito.when(conferenceRepoMock.findByUuid("conf-1")).thenReturn(Optional.of(testConf));
        Mockito.when(sandboxRepoMock.findByConferenceAndUser("conf-1", "user-student-1")).thenReturn(Optional.empty());
        Mockito.when(sandboxRepoMock.findByConferenceUuid("conf-1")).thenReturn(List.of());
        Mockito.doThrow(new RuntimeException("UNIQUE constraint failed"))
            .when(sandboxRepoMock).save(Mockito.any());

        final var ex = assertThrows(IllegalArgumentException.class,
            () -> useCase.execute("conf-1", "user-student-1"));
        assertEquals("sandbox_pool_full", ex.getMessage());
        Mockito.verify(orchestratorMock).deleteSandbox(Mockito.anyString());
    }

    @Test
    void testAssignSandboxKubernetesNotConfigured() {
        Mockito.when(conferenceRepoMock.findByUuid("conf-1")).thenReturn(Optional.of(testConf));
        Mockito.when(sandboxRepoMock.findByConferenceAndUser("conf-1", "user-student-1")).thenReturn(Optional.empty());
        Mockito.when(sandboxRepoMock.findByConferenceUuid("conf-1")).thenReturn(List.of());
        Mockito.doThrow(new IllegalStateException("kubernetes_not_configured"))
            .when(orchestratorMock).createSandbox(Mockito.anyString(), Mockito.anyString(), Mockito.anyString(),
                    Mockito.any(), Mockito.any(), Mockito.anyBoolean());

        final var ex = assertThrows(IllegalArgumentException.class,
            () -> useCase.execute("conf-1", "user-student-1"));
        assertEquals("sandbox_unavailable", ex.getMessage());
    }

    @Test
    void testAssignSandboxClaimsPreProvisionedWithoutTouchingKubernetes() {
        Mockito.when(conferenceRepoMock.findByUuid("conf-1")).thenReturn(Optional.of(testConf));
        Mockito.when(sandboxRepoMock.findByConferenceAndUser("conf-1", "user-student-1")).thenReturn(Optional.empty());
        final var free = new Sandbox("conf-1", 0, null, java.time.Instant.now().plusSeconds(3600));
        Mockito.when(sandboxRepoMock.findUnassigned("conf-1")).thenReturn(Optional.of(free));
        Mockito.when(sandboxRepoMock.claim(Mockito.eq(free.getUuid()), Mockito.eq("user-student-1"), Mockito.any()))
            .thenReturn(true);

        final var result = useCase.execute("conf-1", "user-student-1");

        assertEquals("user-student-1", result.getUserUuid());
        assertEquals(free.getUuid(), result.getUuid());
        Mockito.verifyNoInteractions(orchestratorMock);
        Mockito.verify(sandboxRepoMock, Mockito.never()).save(Mockito.any());
    }

    @Test
    void testAssignSandboxFallsBackToCreateWhenClaimLosesRace() {
        Mockito.when(conferenceRepoMock.findByUuid("conf-1")).thenReturn(Optional.of(testConf));
        Mockito.when(sandboxRepoMock.findByConferenceAndUser("conf-1", "user-student-1")).thenReturn(Optional.empty());
        final var free = new Sandbox("conf-1", 0, null, java.time.Instant.now().plusSeconds(3600));
        Mockito.when(sandboxRepoMock.findUnassigned("conf-1")).thenReturn(Optional.of(free));
        Mockito.when(sandboxRepoMock.claim(Mockito.eq(free.getUuid()), Mockito.eq("user-student-1"), Mockito.any()))
            .thenReturn(false);
        // El otro request que gano la carrera ya ocupo el slot 0; queda libre el slot 1.
        final var takenBySomeoneElse = new Sandbox("conf-1", 0, "user-other", java.time.Instant.now().plusSeconds(3600));
        Mockito.when(sandboxRepoMock.findByConferenceUuid("conf-1")).thenReturn(List.of(takenBySomeoneElse));

        final var result = useCase.execute("conf-1", "user-student-1");

        assertEquals("user-student-1", result.getUserUuid());
        assertEquals(1, result.getSandboxSlot());
        Mockito.verify(orchestratorMock).createSandbox(
            Mockito.eq(result.podName()), Mockito.eq("conf-1"), Mockito.eq("python"),
            Mockito.isNull(), Mockito.isNull(), Mockito.eq(false));
        Mockito.verify(sandboxRepoMock).save(Mockito.any(Sandbox.class));
    }
}
