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

class EnsureUnassignedSandboxUseCaseTest {
    private SandboxRepository sandboxRepoMock;
    private ConferenceRepository conferenceRepoMock;
    private SandboxOrchestrator orchestratorMock;
    private EnsureUnassignedSandboxUseCase useCase;
    private Conference testConf;

    @BeforeEach
    void setup() {
        sandboxRepoMock = Mockito.mock(SandboxRepository.class);
        conferenceRepoMock = Mockito.mock(ConferenceRepository.class);
        orchestratorMock = Mockito.mock(SandboxOrchestrator.class);
        useCase = new EnsureUnassignedSandboxUseCase(sandboxRepoMock, conferenceRepoMock, orchestratorMock, 3600);
        testConf = new Conference("test1", "Test Conference", "user-org-1");
        testConf.setSandboxPoolSize(2);
    }

    @Test
    void testCreatesUnassignedPodsForBothVariantsWhenConferenceHasNone() {
        Mockito.when(conferenceRepoMock.findByUuid("conf-1")).thenReturn(Optional.of(testConf));
        Mockito.when(sandboxRepoMock.findByConferenceUuid("conf-1")).thenReturn(List.of());

        useCase.execute("conf-1");

        Mockito.verify(orchestratorMock).createSandbox(
            Mockito.eq(Sandbox.podName("conf-1", Sandbox.VARIANT_WEB, 0)), Mockito.eq("conf-1"), Mockito.eq("python"),
            Mockito.isNull(), Mockito.eq(false), Mockito.isNull(), Mockito.isNull());
        Mockito.verify(orchestratorMock).createSandbox(
            Mockito.eq(Sandbox.podName("conf-1", Sandbox.VARIANT_CLI, 0)), Mockito.eq("conf-1"), Mockito.eq("terminal-nvim"),
            Mockito.isNull(), Mockito.eq(false), Mockito.isNull(), Mockito.isNull());
        Mockito.verify(sandboxRepoMock, Mockito.times(2)).save(Mockito.any(Sandbox.class));
    }

    @Test
    void testOnlyPreWarmsCliWhenWebAlreadyHasActiveSandbox() {
        Mockito.when(conferenceRepoMock.findByUuid("conf-1")).thenReturn(Optional.of(testConf));
        final var existing = new Sandbox("conf-1", 0, "user-a", java.time.Instant.now().plusSeconds(3600));
        Mockito.when(sandboxRepoMock.findByConferenceUuid("conf-1")).thenReturn(List.of(existing));

        useCase.execute("conf-1");

        Mockito.verify(orchestratorMock, Mockito.never()).createSandbox(
            Mockito.eq(Sandbox.podName("conf-1", Sandbox.VARIANT_WEB, 0)), Mockito.anyString(), Mockito.anyString(),
            Mockito.any(), Mockito.anyBoolean(), Mockito.any(), Mockito.any());
        Mockito.verify(orchestratorMock).createSandbox(
            Mockito.eq(Sandbox.podName("conf-1", Sandbox.VARIANT_CLI, 0)), Mockito.eq("conf-1"), Mockito.eq("terminal-nvim"),
            Mockito.isNull(), Mockito.eq(false), Mockito.isNull(), Mockito.isNull());
        Mockito.verify(sandboxRepoMock, Mockito.times(1)).save(Mockito.any());
    }

    @Test
    void testNoOpWhenConferenceAlreadyHasActiveSandboxOfBothVariants() {
        Mockito.when(conferenceRepoMock.findByUuid("conf-1")).thenReturn(Optional.of(testConf));
        final var web = new Sandbox("conf-1", 0, "user-a", java.time.Instant.now().plusSeconds(3600));
        final var cli = new Sandbox("conf-1", 0, 0, Sandbox.VARIANT_CLI, "user-b",
                java.time.Instant.now().plusSeconds(3600));
        Mockito.when(sandboxRepoMock.findByConferenceUuid("conf-1")).thenReturn(List.of(web, cli));

        useCase.execute("conf-1");

        Mockito.verifyNoInteractions(orchestratorMock);
        Mockito.verify(sandboxRepoMock, Mockito.never()).save(Mockito.any());
    }

    @Test
    void testSwallowsKubernetesNotConfigured() {
        Mockito.when(conferenceRepoMock.findByUuid("conf-1")).thenReturn(Optional.of(testConf));
        Mockito.when(sandboxRepoMock.findByConferenceUuid("conf-1")).thenReturn(List.of());
        Mockito.doThrow(new IllegalStateException("kubernetes_not_configured"))
            .when(orchestratorMock).createSandbox(Mockito.anyString(), Mockito.anyString(), Mockito.anyString(),
                Mockito.any(), Mockito.anyBoolean(), Mockito.any(), Mockito.any());

        assertDoesNotThrow(() -> useCase.execute("conf-1"));
        Mockito.verify(sandboxRepoMock, Mockito.never()).save(Mockito.any());
    }

    @Test
    void testRollsBackPodOnConcurrentPreProvisionRace() {
        Mockito.when(conferenceRepoMock.findByUuid("conf-1")).thenReturn(Optional.of(testConf));
        Mockito.when(sandboxRepoMock.findByConferenceUuid("conf-1")).thenReturn(List.of());
        Mockito.doThrow(new RuntimeException("UNIQUE constraint failed"))
            .when(sandboxRepoMock).save(Mockito.any());

        assertDoesNotThrow(() -> useCase.execute("conf-1"));
        // Una vez por variante (web + cli), cada una pierde su propia carrera de pre-warm.
        Mockito.verify(orchestratorMock, Mockito.times(2)).deleteSandbox(Mockito.anyString());
    }

    @Test
    void testConferenceNotFound() {
        Mockito.when(conferenceRepoMock.findByUuid("nonexistent")).thenReturn(Optional.empty());

        final var ex = assertThrows(IllegalArgumentException.class, () -> useCase.execute("nonexistent"));
        assertEquals("conference_not_found", ex.getMessage());
    }

    @Test
    void ensureSpareOpensNextWebSlotWhenAllCurrentPodsAreOccupied() {
        Mockito.when(conferenceRepoMock.findByUuid("conf-1")).thenReturn(Optional.of(testConf));
        final var occupied = new Sandbox("conf-1", 0, "user-a", java.time.Instant.now().plusSeconds(3600));
        Mockito.when(sandboxRepoMock.findByConferenceUuid("conf-1")).thenReturn(List.of(occupied));

        useCase.ensureSpare("conf-1", Sandbox.VARIANT_WEB);

        Mockito.verify(orchestratorMock).createSandbox(
            Mockito.eq(Sandbox.podName("conf-1", Sandbox.VARIANT_WEB, 1)), Mockito.eq("conf-1"), Mockito.eq("python"),
            Mockito.isNull(), Mockito.eq(false), Mockito.isNull(), Mockito.isNull());
        Mockito.verify(sandboxRepoMock).save(Mockito.any(Sandbox.class));
    }

    @Test
    void ensureSpareReusesFreeSeatInSharedCliPod() {
        testConf.setSandboxCliPoolSize(2);
        testConf.setSandboxSeatsPerPod(4);
        Mockito.when(conferenceRepoMock.findByUuid("conf-1")).thenReturn(Optional.of(testConf));
        final var occupied = new Sandbox("conf-1", 0, 0, Sandbox.VARIANT_CLI,
            "user-a", java.time.Instant.now().plusSeconds(3600));
        Mockito.when(sandboxRepoMock.findByConferenceUuid("conf-1")).thenReturn(List.of(occupied));

        useCase.ensureSpare("conf-1", Sandbox.VARIANT_CLI);

        Mockito.verifyNoInteractions(orchestratorMock);
        Mockito.verify(sandboxRepoMock, Mockito.never()).save(Mockito.any());
    }

    @Test
    void ensureSpareOpensNextCliSlotWhenAllSeatsAreOccupied() {
        testConf.setSandboxCliPoolSize(2);
        testConf.setSandboxSeatsPerPod(4);
        Mockito.when(conferenceRepoMock.findByUuid("conf-1")).thenReturn(Optional.of(testConf));
        final var occupied = java.util.stream.IntStream.range(0, 4)
            .mapToObj(seat -> new Sandbox("conf-1", 0, seat, Sandbox.VARIANT_CLI,
                "user-" + seat, java.time.Instant.now().plusSeconds(3600)))
            .toList();
        Mockito.when(sandboxRepoMock.findByConferenceUuid("conf-1")).thenReturn(occupied);

        useCase.ensureSpare("conf-1", Sandbox.VARIANT_CLI);

        Mockito.verify(orchestratorMock).createSandbox(
            Mockito.eq(Sandbox.podName("conf-1", Sandbox.VARIANT_CLI, 1)), Mockito.eq("conf-1"), Mockito.eq("terminal-nvim"),
            Mockito.isNull(), Mockito.eq(false), Mockito.isNull(), Mockito.eq(4));
        Mockito.verify(sandboxRepoMock).save(Mockito.any(Sandbox.class));
    }
}
