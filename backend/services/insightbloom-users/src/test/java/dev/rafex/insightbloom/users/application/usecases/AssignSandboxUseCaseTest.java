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
                Mockito.isNull(), Mockito.isNull(), Mockito.eq(false), Mockito.isNull(), Mockito.isNull());
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
            Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.any(), Mockito.any(),
            Mockito.anyBoolean(), Mockito.any(), Mockito.any());
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
            Mockito.isNull(), Mockito.isNull(), Mockito.eq(false), Mockito.isNull(), Mockito.isNull());
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
                    Mockito.any(), Mockito.any(), Mockito.anyBoolean(), Mockito.any(), Mockito.any());

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
            Mockito.isNull(), Mockito.isNull(), Mockito.eq(false), Mockito.isNull(), Mockito.isNull());
        Mockito.verify(sandboxRepoMock).save(Mockito.any(Sandbox.class));
    }

    @Test
    void testSharedNeovimPodFillsSeatsBeforeOpeningNewPod() {
        // seatsPerPod=4, poolSize=2 -> capacidad total 8. Los primeros 4 alumnos deben compartir
        // el pod del slot 0 (seatIndex 0..3, sin volver a llamar createSandbox salvo para el
        // primero); el 5to abre el slot 1 (Pod nuevo).
        testConf.setSandboxVariant("terminal-nvim");
        testConf.setSandboxSeatsPerPod(4);
        Mockito.when(conferenceRepoMock.findByUuid("conf-1")).thenReturn(Optional.of(testConf));
        Mockito.when(sandboxRepoMock.findByConferenceAndUser(Mockito.eq("conf-1"), Mockito.anyString()))
            .thenReturn(Optional.empty());
        Mockito.when(sandboxRepoMock.findUnassigned("conf-1")).thenReturn(Optional.empty());

        // Alumno 1: slot 0 esta vacio -> abre Pod nuevo, seatIndex 0.
        Mockito.when(sandboxRepoMock.findByConferenceUuid("conf-1")).thenReturn(List.of());
        final var s1 = useCase.execute("conf-1", "user-1");
        assertEquals(0, s1.getSandboxSlot());
        assertEquals(0, s1.getSeatIndex());
        Mockito.verify(orchestratorMock, Mockito.times(1)).createSandbox(
            Mockito.eq(s1.podName()), Mockito.anyString(), Mockito.eq("terminal-nvim"),
            Mockito.any(), Mockito.any(), Mockito.anyBoolean(), Mockito.any(), Mockito.eq(4));

        // Alumnos 2-4: mismo slot 0, ya tiene Pod -- se suman como asientos 1, 2, 3, SIN volver
        // a llamar createSandbox (el pod ya existe).
        Mockito.when(sandboxRepoMock.findByConferenceUuid("conf-1")).thenReturn(List.of(s1));
        final var s2 = useCase.execute("conf-1", "user-2");
        assertEquals(0, s2.getSandboxSlot());
        assertEquals(1, s2.getSeatIndex());

        Mockito.when(sandboxRepoMock.findByConferenceUuid("conf-1")).thenReturn(List.of(s1, s2));
        final var s3 = useCase.execute("conf-1", "user-3");
        assertEquals(2, s3.getSeatIndex());

        Mockito.when(sandboxRepoMock.findByConferenceUuid("conf-1")).thenReturn(List.of(s1, s2, s3));
        final var s4 = useCase.execute("conf-1", "user-4");
        assertEquals(3, s4.getSeatIndex());

        // Solo UNA llamada a createSandbox en total (la del primer alumno) para los 4.
        Mockito.verify(orchestratorMock, Mockito.times(1)).createSandbox(
            Mockito.anyString(), Mockito.anyString(), Mockito.anyString(),
            Mockito.any(), Mockito.any(), Mockito.anyBoolean(), Mockito.any(), Mockito.any());

        // Alumno 5: slot 0 lleno (4/4) -> abre slot 1, seatIndex 0, Pod nuevo.
        Mockito.when(sandboxRepoMock.findByConferenceUuid("conf-1")).thenReturn(List.of(s1, s2, s3, s4));
        final var s5 = useCase.execute("conf-1", "user-5");
        assertEquals(1, s5.getSandboxSlot());
        assertEquals(0, s5.getSeatIndex());
        Mockito.verify(orchestratorMock, Mockito.times(1)).createSandbox(
            Mockito.eq(s5.podName()), Mockito.anyString(), Mockito.eq("terminal-nvim"),
            Mockito.any(), Mockito.any(), Mockito.anyBoolean(), Mockito.any(), Mockito.eq(4));
    }

    @Test
    void testSharedNeovimPodFullAcrossAllPodsThrows() {
        testConf.setSandboxVariant("terminal-nvim");
        testConf.setSandboxSeatsPerPod(4);
        testConf.setSandboxPoolSize(2); // capacidad total = 2*4 = 8
        Mockito.when(conferenceRepoMock.findByUuid("conf-1")).thenReturn(Optional.of(testConf));
        Mockito.when(sandboxRepoMock.findByConferenceAndUser(Mockito.eq("conf-1"), Mockito.anyString()))
            .thenReturn(Optional.empty());
        Mockito.when(sandboxRepoMock.findUnassigned("conf-1")).thenReturn(Optional.empty());
        final List<Sandbox> full = new java.util.ArrayList<>();
        for (int slot = 0; slot < 2; slot++) {
            for (int seat = 0; seat < 4; seat++) {
                full.add(new Sandbox("conf-1", slot, seat, "user-" + slot + "-" + seat,
                        java.time.Instant.now().plusSeconds(3600)));
            }
        }
        Mockito.when(sandboxRepoMock.findByConferenceUuid("conf-1")).thenReturn(full);

        final var ex = assertThrows(IllegalArgumentException.class,
            () -> useCase.execute("conf-1", "user-nuevo"));
        assertEquals("sandbox_pool_full", ex.getMessage());
    }
}
