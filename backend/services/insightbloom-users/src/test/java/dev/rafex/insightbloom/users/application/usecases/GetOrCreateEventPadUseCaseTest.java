package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.CanvasConfig;
import dev.rafex.insightbloom.users.domain.model.Conference;
import dev.rafex.insightbloom.users.domain.ports.ConferenceRepository;
import dev.rafex.insightbloom.users.domain.ports.EtherpadPort;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GetOrCreateEventPadUseCaseTest {
    @Test
    void derivesOnePrivatePadPerUserForIndividualNotes() {
        final ConferenceRepository repository = mock(ConferenceRepository.class);
        final EtherpadPort etherpad = mock(EtherpadPort.class);
        final Conference conference = new Conference("evento", "Evento", "owner");
        conference.setCanvasConfigs(List.of(new CanvasConfig("ETHERPAD", "INDEPENDENT")));
        when(repository.findByUuid(conference.getUuid())).thenReturn(Optional.of(conference));

        final var useCase = new GetOrCreateEventPadUseCase(repository, etherpad, "stable-secret");
        final String first = useCase.execute(conference.getUuid(), "user-a").orElseThrow().padId();
        final String second = useCase.execute(conference.getUuid(), "user-b").orElseThrow().padId();

        assertNotEquals(first, second);
        assertTrue(first.startsWith(conference.getUuid() + "--private--"));
        assertTrue(second.startsWith(conference.getUuid() + "--private--"));
    }

    @Test
    void usesTheEventPadForCollaborativeNotes() {
        final ConferenceRepository repository = mock(ConferenceRepository.class);
        final EtherpadPort etherpad = mock(EtherpadPort.class);
        final Conference conference = new Conference("evento", "Evento", "owner");
        conference.setCanvasConfigs(List.of(new CanvasConfig("ETHERPAD", "COLLABORATIVE")));
        when(repository.findByUuid(conference.getUuid())).thenReturn(Optional.of(conference));

        final var pad = new GetOrCreateEventPadUseCase(repository, etherpad, "stable-secret")
                .execute(conference.getUuid(), "user-a").orElseThrow();

        assertEquals(conference.getUuid(), pad.padId());
    }
}
