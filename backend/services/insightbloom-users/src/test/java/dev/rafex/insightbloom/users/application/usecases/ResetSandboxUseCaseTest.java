package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.Conference;
import dev.rafex.insightbloom.users.domain.model.Sandbox;
import dev.rafex.insightbloom.users.domain.ports.ConferenceRepository;
import dev.rafex.insightbloom.users.domain.ports.SandboxOrchestrator;
import dev.rafex.insightbloom.users.domain.ports.SandboxRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class ResetSandboxUseCaseTest {
    @Test
    void refusesToResetPodWithAssignedSeat() {
        final SandboxRepository sandboxRepository = mock(SandboxRepository.class);
        final ConferenceRepository conferenceRepository = mock(ConferenceRepository.class);
        final SandboxOrchestrator orchestrator = mock(SandboxOrchestrator.class);
        final EnsureUnassignedSandboxUseCase ensurePool = mock(EnsureUnassignedSandboxUseCase.class);
        final Conference conference = new Conference("conf-1", "Demo", "owner");
        final Sandbox assigned = new Sandbox("conf-1", 0, 0, Sandbox.VARIANT_CLI,
            "student-1", Instant.now().plusSeconds(3600));
        when(conferenceRepository.findByUuid("conf-1")).thenReturn(Optional.of(conference));
        when(sandboxRepository.findByUuid(assigned.getUuid())).thenReturn(Optional.of(assigned));
        when(sandboxRepository.findByConferenceUuid("conf-1")).thenReturn(List.of(assigned));

        final var useCase = new ResetSandboxUseCase(
            sandboxRepository, conferenceRepository, orchestrator, ensurePool);

        final var error = assertThrows(IllegalArgumentException.class,
            () -> useCase.recreate("conf-1", assigned.getUuid()));

        assertEquals("sandbox_in_use", error.getMessage());
        verifyNoInteractions(orchestrator, ensurePool);
        verify(sandboxRepository, never()).deleteByUuid(anyString());
    }

    @Test
    void recreatesFreePodAndRestoresConfiguredPoolSlot() {
        final SandboxRepository sandboxRepository = mock(SandboxRepository.class);
        final ConferenceRepository conferenceRepository = mock(ConferenceRepository.class);
        final SandboxOrchestrator orchestrator = mock(SandboxOrchestrator.class);
        final EnsureUnassignedSandboxUseCase ensurePool = mock(EnsureUnassignedSandboxUseCase.class);
        final Conference conference = new Conference("conf-1", "Demo", "owner");
        conference.setSandboxPoolSize(2);
        final Sandbox free = new Sandbox("conf-1", 0, 0, Sandbox.VARIANT_WEB,
            null, Instant.now().plusSeconds(3600));
        when(conferenceRepository.findByUuid("conf-1")).thenReturn(Optional.of(conference));
        when(sandboxRepository.findByUuid(free.getUuid())).thenReturn(Optional.of(free));
        when(sandboxRepository.findByConferenceUuid("conf-1")).thenReturn(List.of(free));
        when(ensurePool.ensurePool("conf-1", Sandbox.VARIANT_WEB, 2)).thenReturn(1);

        final var useCase = new ResetSandboxUseCase(
            sandboxRepository, conferenceRepository, orchestrator, ensurePool);

        final var result = useCase.recreate("conf-1", free.getUuid());

        assertEquals("recreated", result.action());
        assertEquals(1, result.recreatedPods());
        verify(orchestrator).deleteSandbox(free.podName());
        verify(sandboxRepository).deletePod("conf-1", Sandbox.VARIANT_WEB, 0);
        verify(ensurePool).ensurePool("conf-1", Sandbox.VARIANT_WEB, 2);
    }

    @Test
    void deletesOccupiedPodAndAllSharedSeatRows() {
        final SandboxRepository sandboxRepository = mock(SandboxRepository.class);
        final ConferenceRepository conferenceRepository = mock(ConferenceRepository.class);
        final SandboxOrchestrator orchestrator = mock(SandboxOrchestrator.class);
        final EnsureUnassignedSandboxUseCase ensurePool = mock(EnsureUnassignedSandboxUseCase.class);
        final Conference conference = new Conference("conf-1", "Demo", "owner");
        final Sandbox assigned = new Sandbox("conf-1", 0, 0, Sandbox.VARIANT_CLI,
            "student-1", Instant.now().plusSeconds(3600));
        final Sandbox freeSeat = new Sandbox("conf-1", 0, 1, Sandbox.VARIANT_CLI,
            null, Instant.now().plusSeconds(3600));
        when(conferenceRepository.findByUuid("conf-1")).thenReturn(Optional.of(conference));
        when(sandboxRepository.findByUuid(assigned.getUuid())).thenReturn(Optional.of(assigned));
        when(sandboxRepository.findByConferenceUuid("conf-1")).thenReturn(List.of(assigned, freeSeat));

        final var useCase = new ResetSandboxUseCase(
            sandboxRepository, conferenceRepository, orchestrator, ensurePool);

        final var result = useCase.delete("conf-1", assigned.getUuid());

        assertEquals("deleted", result.action());
        verify(orchestrator).deleteSandbox(assigned.podName());
        verify(sandboxRepository).deletePod("conf-1", Sandbox.VARIANT_CLI, 0);
        verify(conferenceRepository, never()).findByUuid("conf-1");
        verify(conferenceRepository, never()).save(any(Conference.class));
        verifyNoInteractions(ensurePool);
    }
}
