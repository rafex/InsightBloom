package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.Conference;
import dev.rafex.insightbloom.users.domain.model.EgressPolicy;
import dev.rafex.insightbloom.users.domain.model.PlatformSettings;
import dev.rafex.insightbloom.users.domain.ports.ConferenceRepository;
import dev.rafex.insightbloom.users.domain.ports.EgressPolicyRepository;
import dev.rafex.insightbloom.users.domain.ports.PlatformSettingsRepository;
import dev.rafex.insightbloom.users.domain.ports.SandboxOrchestrator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ResolveEgressPolicyUseCaseTest {

    private static final String IP = "10.42.0.149";
    private static final String CONF = "conf-1";

    private SandboxOrchestrator orchestrator;
    private ConferenceRepository conferenceRepository;
    private PlatformSettingsRepository platformSettingsRepository;
    private EgressPolicyRepository egressPolicyRepository;
    private ResolveEgressPolicyUseCase useCase;

    @BeforeEach
    void setUp() {
        orchestrator = Mockito.mock(SandboxOrchestrator.class);
        conferenceRepository = Mockito.mock(ConferenceRepository.class);
        platformSettingsRepository = Mockito.mock(PlatformSettingsRepository.class);
        egressPolicyRepository = Mockito.mock(EgressPolicyRepository.class);
        useCase = new ResolveEgressPolicyUseCase(orchestrator, conferenceRepository, platformSettingsRepository,
                egressPolicyRepository);
    }

    private static Conference conferenceWithInternet(final int internetEnabled) {
        final Conference conference = new Conference("evt-1", "Evento de prueba", "user-1");
        conference.setSandboxInternetEnabled(internetEnabled);
        return conference;
    }

    @Test
    void returnsEmptyWhenIpDoesNotMatchAnySandbox() {
        Mockito.when(orchestrator.findConferenceUuidByPodIp(IP)).thenReturn(Optional.empty());

        assertTrue(useCase.execute(IP).isEmpty());
        Mockito.verifyNoInteractions(conferenceRepository, platformSettingsRepository, egressPolicyRepository);
    }

    @Test
    void unionsGlobalAndEventAllowedAndBlockedLists() {
        Mockito.when(orchestrator.findConferenceUuidByPodIp(IP)).thenReturn(Optional.of(CONF));
        Mockito.when(conferenceRepository.findByUuid(CONF)).thenReturn(Optional.of(conferenceWithInternet(1)));
        final PlatformSettings global = PlatformSettings.defaults();
        global.setEgressAllowedHosts("github.com,*.npmjs.org");
        global.setEgressBlockedHosts("evil.example.com");
        Mockito.when(platformSettingsRepository.get()).thenReturn(global);
        Mockito.when(egressPolicyRepository.findByConference(CONF)).thenReturn(Optional.of(
                new EgressPolicy(CONF, "extra-allowed.example.com", "extra-blocked.example.com", Instant.now())));

        final var resolution = useCase.execute(IP).orElseThrow();

        assertEquals(CONF, resolution.conferenceUuid());
        assertTrue(resolution.internetEnabled());
        assertTrue(resolution.allowed().contains("github.com"));
        assertTrue(resolution.allowed().contains("*.npmjs.org"));
        assertTrue(resolution.allowed().contains("extra-allowed.example.com"));
        assertTrue(resolution.blocked().contains("evil.example.com"));
        assertTrue(resolution.blocked().contains("extra-blocked.example.com"));
    }

    @Test
    void eventBlockedHostAlsoAppliesEvenIfNotInGlobalBlockedList() {
        // Opcion C: blocked = global UNION evento -- un evento puede AGREGAR bloqueos propios,
        // nunca "desbloquear" algo que el global ya prohibe (eso solo pasa si simplemente no
        // se repite en la lista del evento, que es justo lo que se prueba aca: el evento no
        // toca allowed/blocked globales, solo suma su propio bloqueo).
        Mockito.when(orchestrator.findConferenceUuidByPodIp(IP)).thenReturn(Optional.of(CONF));
        Mockito.when(conferenceRepository.findByUuid(CONF)).thenReturn(Optional.of(conferenceWithInternet(1)));
        final PlatformSettings global = PlatformSettings.defaults();
        global.setEgressAllowedHosts("github.com");
        global.setEgressBlockedHosts(null);
        Mockito.when(platformSettingsRepository.get()).thenReturn(global);
        Mockito.when(egressPolicyRepository.findByConference(CONF)).thenReturn(Optional.of(
                new EgressPolicy(CONF, null, "github.com", Instant.now())));

        final var resolution = useCase.execute(IP).orElseThrow();

        // github.com sigue en allowed (lo puso el global) Y en blocked (lo agrego el evento) --
        // la decision de "quien gana" es responsabilidad del proxy (blocked > allowed), este
        // caso de uso solo combina las listas, no resuelve la precedencia.
        assertTrue(resolution.allowed().contains("github.com"));
        assertTrue(resolution.blocked().contains("github.com"));
    }

    @Test
    void worksWithoutAnyEventSpecificPolicy() {
        Mockito.when(orchestrator.findConferenceUuidByPodIp(IP)).thenReturn(Optional.of(CONF));
        Mockito.when(conferenceRepository.findByUuid(CONF)).thenReturn(Optional.of(conferenceWithInternet(0)));
        final PlatformSettings global = PlatformSettings.defaults();
        global.setEgressAllowedHosts("github.com");
        global.setEgressBlockedHosts("localhost");
        Mockito.when(platformSettingsRepository.get()).thenReturn(global);
        Mockito.when(egressPolicyRepository.findByConference(CONF)).thenReturn(Optional.empty());

        final var resolution = useCase.execute(IP).orElseThrow();

        assertFalse(resolution.internetEnabled());
        assertEquals(Set.of("github.com"), resolution.allowed());
        assertEquals(Set.of("localhost"), resolution.blocked());
    }

    @Test
    void returnsEmptyWhenConferenceNoLongerExists() {
        Mockito.when(orchestrator.findConferenceUuidByPodIp(IP)).thenReturn(Optional.of(CONF));
        Mockito.when(conferenceRepository.findByUuid(CONF)).thenReturn(Optional.empty());

        assertTrue(useCase.execute(IP).isEmpty());
    }
}
