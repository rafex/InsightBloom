package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.Conference;
import dev.rafex.insightbloom.users.domain.model.Sandbox;
import dev.rafex.insightbloom.users.domain.ports.ConferenceRepository;
import dev.rafex.insightbloom.users.domain.ports.SandboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class GetSandboxAvailabilityUseCaseTest {
    private ConferenceRepository conferenceRepoMock;
    private SandboxRepository sandboxRepoMock;
    private GetSandboxAvailabilityUseCase useCase;
    private Conference testConf;

    @BeforeEach
    void setup() {
        conferenceRepoMock = Mockito.mock(ConferenceRepository.class);
        sandboxRepoMock = Mockito.mock(SandboxRepository.class);
        useCase = new GetSandboxAvailabilityUseCase(conferenceRepoMock, sandboxRepoMock);
        testConf = new Conference("test1", "Test Conference", "user-org-1");
        testConf.setSandboxPoolSize(1);
        testConf.setSandboxCliPoolSize(1);
        testConf.setSandboxSeatsPerPod(4);
    }

    @Test
    void testBothVariantsAvailableWhenEmpty() {
        Mockito.when(conferenceRepoMock.findByUuid("conf-1")).thenReturn(Optional.of(testConf));
        Mockito.when(sandboxRepoMock.findByConferenceUuid("conf-1")).thenReturn(List.of());
        Mockito.when(sandboxRepoMock.findByConferenceAndUser("conf-1", "user-a")).thenReturn(Optional.empty());

        final var availability = useCase.execute("conf-1", "user-a");

        assertTrue(availability.web().available());
        assertEquals(0, availability.web().activeCount());
        assertEquals(1, availability.web().capacity());
        assertTrue(availability.cli().available());
        assertEquals(0, availability.cli().activeCount());
        assertEquals(4, availability.cli().capacity()); // 1 pod * 4 asientos
    }

    @Test
    void testWebUnavailableWhenPoolFullButCliStaysAvailable() {
        Mockito.when(conferenceRepoMock.findByUuid("conf-1")).thenReturn(Optional.of(testConf));
        final var web = new Sandbox("conf-1", 0, "user-a", Instant.now().plusSeconds(3600));
        Mockito.when(sandboxRepoMock.findByConferenceUuid("conf-1")).thenReturn(List.of(web));
        Mockito.when(sandboxRepoMock.findByConferenceAndUser("conf-1", "user-b")).thenReturn(Optional.empty());

        final var availability = useCase.execute("conf-1", "user-b");

        assertFalse(availability.web().available());
        assertEquals(1, availability.web().activeCount());
        assertTrue(availability.cli().available());
    }

    @Test
    void testPrewarmedUnassignedPodsDoNotConsumeAvailability() {
        Mockito.when(conferenceRepoMock.findByUuid("conf-1")).thenReturn(Optional.of(testConf));
        final var preparedWeb = new Sandbox("conf-1", 0, 0, Sandbox.VARIANT_WEB,
            null, Instant.now().plusSeconds(3600));
        final var preparedCli = new Sandbox("conf-1", 0, 0, Sandbox.VARIANT_CLI,
            null, Instant.now().plusSeconds(3600));
        Mockito.when(sandboxRepoMock.findByConferenceUuid("conf-1"))
            .thenReturn(List.of(preparedWeb, preparedCli));
        Mockito.when(sandboxRepoMock.findByConferenceAndUser("conf-1", "user-a"))
            .thenReturn(Optional.empty());

        final var availability = useCase.execute("conf-1", "user-a");

        assertEquals(0, availability.web().activeCount());
        assertTrue(availability.web().available());
        assertEquals(0, availability.cli().activeCount());
        assertTrue(availability.cli().available());
    }

    @Test
    void testOwnerOfFullPoolCanStillReconnect() {
        Mockito.when(conferenceRepoMock.findByUuid("conf-1")).thenReturn(Optional.of(testConf));
        final var web = new Sandbox("conf-1", 0, "user-a", Instant.now().plusSeconds(3600));
        Mockito.when(sandboxRepoMock.findByConferenceUuid("conf-1")).thenReturn(List.of(web));
        Mockito.when(sandboxRepoMock.findByConferenceAndUser("conf-1", "user-a")).thenReturn(Optional.of(web));

        final var availability = useCase.execute("conf-1", "user-a");

        assertTrue(availability.web().available());
        assertEquals(1, availability.web().activeCount());
        assertEquals(1, availability.web().capacity());
    }

    @Test
    void testLazyVimHasIndependentCapacityWhenOrganizerEnablesIt() {
        testConf.setSandboxCliLazyVimPoolSize(1);
        Mockito.when(conferenceRepoMock.findByUuid("conf-1")).thenReturn(Optional.of(testConf));
        final var lazyVim = new Sandbox("conf-1", 0, 0, Sandbox.VARIANT_CLI_LAZYVIM,
            "user-a", Instant.now().plusSeconds(3600));
        Mockito.when(sandboxRepoMock.findByConferenceUuid("conf-1")).thenReturn(List.of(lazyVim));
        Mockito.when(sandboxRepoMock.findByConferenceAndUser("conf-1", "user-b"))
            .thenReturn(Optional.empty());

        final var availability = useCase.execute("conf-1", "user-b");

        assertEquals(1, availability.cliLazyVim().activeCount());
        assertEquals(4, availability.cliLazyVim().capacity());
        assertTrue(availability.cliLazyVim().available());
        assertEquals(0, availability.cli().activeCount());
    }

    @Test
    void testConferenceNotFound() {
        Mockito.when(conferenceRepoMock.findByUuid("nonexistent")).thenReturn(Optional.empty());

        final var ex = assertThrows(IllegalArgumentException.class, () -> useCase.execute("nonexistent", "user-a"));
        assertEquals("conference_not_found", ex.getMessage());
    }
}
