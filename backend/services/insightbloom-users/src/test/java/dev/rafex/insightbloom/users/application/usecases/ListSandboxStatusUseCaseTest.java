package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.Sandbox;
import dev.rafex.insightbloom.users.domain.model.User;
import dev.rafex.insightbloom.users.domain.model.UserRole;
import dev.rafex.insightbloom.users.domain.ports.SandboxOrchestrator;
import dev.rafex.insightbloom.users.domain.ports.SandboxRepository;
import dev.rafex.insightbloom.users.domain.ports.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ListSandboxStatusUseCaseTest {
    private SandboxRepository sandboxRepoMock;
    private SandboxOrchestrator orchestratorMock;
    private UserRepository userRepoMock;
    private ListSandboxStatusUseCase useCase;

    @BeforeEach
    void setup() {
        sandboxRepoMock = Mockito.mock(SandboxRepository.class);
        orchestratorMock = Mockito.mock(SandboxOrchestrator.class);
        userRepoMock = Mockito.mock(UserRepository.class);
        useCase = new ListSandboxStatusUseCase(sandboxRepoMock, orchestratorMock, userRepoMock);
    }

    @Test
    void testDedupesSharedCliPodIntoOneStatusEntry() {
        // Un Pod "cli" de 4 asientos con 2 ocupados debe dar UNA fila de estado con 2
        // asientos, no 2 filas -- getPhase/isReady se llaman una sola vez por Pod real.
        final Instant expiresAt = Instant.now().plusSeconds(3600);
        final var seat0 = new Sandbox("conf-1", 0, 0, Sandbox.VARIANT_CLI, "user-a", expiresAt);
        final var seat1 = new Sandbox("conf-1", 0, 1, Sandbox.VARIANT_CLI, "user-b", expiresAt);
        Mockito.when(sandboxRepoMock.findByConferenceUuid("conf-1")).thenReturn(List.of(seat0, seat1));
        Mockito.when(orchestratorMock.getPhase(seat0.podName())).thenReturn("Running");
        Mockito.when(orchestratorMock.isReady(seat0.podName())).thenReturn(true);

        final var result = useCase.execute("conf-1");

        assertEquals(1, result.size());
        final var status = result.get(0);
        assertEquals(seat0.podName(), status.podName());
        assertEquals(Sandbox.VARIANT_CLI, status.variant());
        assertEquals("Running", status.phase());
        assertTrue(status.ready());
        assertEquals(2, status.seats().size());
        Mockito.verify(orchestratorMock, Mockito.times(1)).getPhase(seat0.podName());
    }

    @Test
    void testWebAndCliPodsListedSeparately() {
        final Instant expiresAt = Instant.now().plusSeconds(3600);
        final var web = new Sandbox("conf-1", 0, "user-a", expiresAt);
        final var cli = new Sandbox("conf-1", 0, 0, Sandbox.VARIANT_CLI, "user-b", expiresAt);
        Mockito.when(sandboxRepoMock.findByConferenceUuid("conf-1")).thenReturn(List.of(web, cli));
        Mockito.when(orchestratorMock.getPhase(Mockito.anyString())).thenReturn(null);

        final var result = useCase.execute("conf-1");

        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(s -> "NotFound".equals(s.phase()) && !s.ready()));
    }

    @Test
    void includesDisplayNameForOccupiedSeatsWithoutExposingItAsTheIdentifier() {
        final Instant expiresAt = Instant.now().plusSeconds(3600);
        final var seat = new Sandbox("conf-1", 0, "user-a", expiresAt);
        final var user = new User("user-a", "alice", "Alice Example", "alice@example.com", UserRole.ATTENDEE);
        Mockito.when(sandboxRepoMock.findByConferenceUuid("conf-1")).thenReturn(List.of(seat));
        Mockito.when(userRepoMock.findByUuid("user-a")).thenReturn(java.util.Optional.of(user));
        Mockito.when(orchestratorMock.getRuntimeStatus(seat.podName())).thenReturn(null);

        final var result = useCase.execute("conf-1");

        assertEquals("Alice Example", result.get(0).seats().get(0).userDisplayName());
        assertEquals("user-a", result.get(0).seats().get(0).userUuid());
    }
}
