package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.CanvasAudienceMode;
import dev.rafex.insightbloom.users.domain.model.CanvasConfig;
import dev.rafex.insightbloom.users.domain.model.CanvasTool;
import dev.rafex.insightbloom.users.domain.model.Conference;
import dev.rafex.insightbloom.users.domain.ports.ConferenceRepository;
import dev.rafex.insightbloom.users.domain.ports.EtherpadPort;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GetOrCreateEventPadUseCaseTest {
    @Test
    void derivesOnePrivatePadPerUserForIndividualNotes() {
        final ConferenceRepository repository = mock(ConferenceRepository.class);
        final EtherpadPort etherpad = mock(EtherpadPort.class);
        final Conference conference = new Conference("evento", "Evento", "owner");
        conference.setCanvasConfigs(List.of(new CanvasConfig(CanvasTool.ETHERPAD, CanvasAudienceMode.INDEPENDENT)));
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
        conference.setCanvasConfigs(List.of(new CanvasConfig(CanvasTool.ETHERPAD, CanvasAudienceMode.COLLABORATIVE)));
        when(repository.findByUuid(conference.getUuid())).thenReturn(Optional.of(conference));

        final var pad = new GetOrCreateEventPadUseCase(repository, etherpad, "stable-secret")
                .execute(conference.getUuid(), "user-a").orElseThrow();

        assertEquals(conference.getUuid(), pad.padId());
    }

    @Test
    void moderatorGetsTheRealWritablePadInModeratorOnlyMode() {
        final ConferenceRepository repository = mock(ConferenceRepository.class);
        final EtherpadPort etherpad = mock(EtherpadPort.class);
        final Conference conference = new Conference("evento", "Evento", "owner");
        conference.setCanvasConfigs(List.of(new CanvasConfig(CanvasTool.ETHERPAD, CanvasAudienceMode.MODERATOR_ONLY)));
        when(repository.findByUuid(conference.getUuid())).thenReturn(Optional.of(conference));

        final var pad = new GetOrCreateEventPadUseCase(repository, etherpad, "stable-secret")
                .execute(conference.getUuid(), "owner", true).orElseThrow();

        assertEquals(conference.getUuid(), pad.padId());
        assertFalse(pad.readOnly());
        verify(etherpad, never()).getReadOnlyId(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void attendeeGetsEtherpadsRealReadOnlyIdInModeratorOnlyMode() {
        final ConferenceRepository repository = mock(ConferenceRepository.class);
        final EtherpadPort etherpad = mock(EtherpadPort.class);
        final Conference conference = new Conference("evento", "Evento", "owner");
        conference.setCanvasConfigs(List.of(new CanvasConfig(CanvasTool.ETHERPAD, CanvasAudienceMode.MODERATOR_ONLY)));
        when(repository.findByUuid(conference.getUuid())).thenReturn(Optional.of(conference));
        when(etherpad.getReadOnlyId(conference.getUuid())).thenReturn("r.abc123");

        final var pad = new GetOrCreateEventPadUseCase(repository, etherpad, "stable-secret")
                .execute(conference.getUuid(), "attendee", false).orElseThrow();

        assertEquals("r.abc123", pad.padId());
        assertTrue(pad.readOnly());
    }

    @Test
    void attendeeStaysWritableInCollaborativeMode() {
        final ConferenceRepository repository = mock(ConferenceRepository.class);
        final EtherpadPort etherpad = mock(EtherpadPort.class);
        final Conference conference = new Conference("evento", "Evento", "owner");
        conference.setCanvasConfigs(List.of(new CanvasConfig(CanvasTool.ETHERPAD, CanvasAudienceMode.COLLABORATIVE)));
        when(repository.findByUuid(conference.getUuid())).thenReturn(Optional.of(conference));

        final var pad = new GetOrCreateEventPadUseCase(repository, etherpad, "stable-secret")
                .execute(conference.getUuid(), "attendee", false).orElseThrow();

        assertEquals(conference.getUuid(), pad.padId());
        assertFalse(pad.readOnly());
    }
}
