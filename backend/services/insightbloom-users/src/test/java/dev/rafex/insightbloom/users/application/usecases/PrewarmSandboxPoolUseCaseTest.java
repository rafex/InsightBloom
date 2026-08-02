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

import static org.junit.jupiter.api.Assertions.assertEquals;

class PrewarmSandboxPoolUseCaseTest {
    private SandboxRepository sandboxRepository;
    private ConferenceRepository conferenceRepository;
    private SandboxOrchestrator orchestrator;
    private Conference conference;
    private PrewarmSandboxPoolUseCase useCase;

    @BeforeEach
    void setUp() {
        sandboxRepository = Mockito.mock(SandboxRepository.class);
        conferenceRepository = Mockito.mock(ConferenceRepository.class);
        orchestrator = Mockito.mock(SandboxOrchestrator.class);
        final var ensurePool = new EnsureUnassignedSandboxUseCase(
            sandboxRepository, conferenceRepository, orchestrator, 3600);
        useCase = new PrewarmSandboxPoolUseCase(conferenceRepository, ensurePool);
        conference = new Conference("demo", "Demo", "owner");
        conference.setSandboxPoolSize(2);
        conference.setSandboxCliPoolSize(3);
        conference.setSandboxSeatsPerPod(4);

        Mockito.when(conferenceRepository.findByUuid("conf-1")).thenReturn(Optional.of(conference));
        Mockito.when(sandboxRepository.findByConferenceUuid("conf-1")).thenReturn(List.of());
    }

    @Test
    void prewarmsConfiguredWebAndCliPoolsIdempotently() {
        final var result = useCase.execute("conf-1");

        assertEquals("conf-1", result.conferenceUuid());
        assertEquals(new PrewarmSandboxPoolUseCase.VariantResult(
            Sandbox.VARIANT_WEB, 2, 2), result.variants().get(0));
        assertEquals(new PrewarmSandboxPoolUseCase.VariantResult(
            Sandbox.VARIANT_CLI, 3, 3), result.variants().get(1));

        Mockito.verify(orchestrator, Mockito.times(5)).createSandbox(
            Mockito.anyString(), Mockito.eq("conf-1"), Mockito.anyString(),
            Mockito.isNull(), Mockito.eq(false), Mockito.isNull(), Mockito.eq(4));
        Mockito.verify(sandboxRepository, Mockito.times(5)).save(Mockito.any(Sandbox.class));
    }

    @Test
    void doesNotCreatePodsAlreadyPresentInThePool() {
        final var web = new Sandbox("conf-1", 0, 0, Sandbox.VARIANT_WEB, null, null);
        final var cli = new Sandbox("conf-1", 0, 0, Sandbox.VARIANT_CLI, null, null);
        Mockito.when(sandboxRepository.findByConferenceUuid("conf-1")).thenReturn(List.of(web, cli));

        final var result = useCase.execute("conf-1");

        assertEquals(1, result.variants().get(0).createdPods());
        assertEquals(2, result.variants().get(1).createdPods());
        Mockito.verify(orchestrator, Mockito.times(3)).createSandbox(
            Mockito.anyString(), Mockito.eq("conf-1"), Mockito.anyString(),
            Mockito.isNull(), Mockito.eq(false), Mockito.isNull(), Mockito.eq(4));
        Mockito.verify(sandboxRepository, Mockito.times(3)).save(Mockito.any(Sandbox.class));
    }

    @Test
    void prewarmsLazyVimAsAnIndependentOptInPool() {
        conference.setSandboxCliLazyVimPoolSize(1);

        final var result = useCase.execute("conf-1");

        assertEquals(new PrewarmSandboxPoolUseCase.VariantResult(
            Sandbox.VARIANT_CLI_LAZYVIM, 1, 1), result.variants().get(2));
        Mockito.verify(orchestrator, Mockito.times(6)).createSandbox(
            Mockito.anyString(), Mockito.eq("conf-1"), Mockito.anyString(),
            Mockito.isNull(), Mockito.eq(false), Mockito.isNull(), Mockito.eq(4));
    }
}
