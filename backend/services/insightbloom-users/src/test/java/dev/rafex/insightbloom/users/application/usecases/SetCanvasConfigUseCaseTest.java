package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.Conference;
import dev.rafex.insightbloom.users.domain.ports.ConferenceRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SetCanvasConfigUseCaseTest {
    @Test
    void savesToolAndAudienceModeForEventOwner() {
        final ConferenceRepository repository = mock(ConferenceRepository.class);
        final Conference conference = new Conference("evento", "Evento", "owner");
        when(repository.findByUuid(conference.getUuid())).thenReturn(Optional.of(conference));

        final var result = new SetCanvasConfigUseCase(repository)
                .execute(conference.getUuid(), "owner", "EXCALIDRAW", "MODERATOR_ONLY");

        assertTrue(result.isPresent());
        assertEquals("EXCALIDRAW", conference.getCanvasTool());
        assertEquals("MODERATOR_ONLY", conference.getCanvasAudienceMode());
        verify(repository).save(conference);
        verify(repository).replaceCanvasConfigs(conference.getUuid(), conference.getCanvasConfigs());
    }

    @Test
    void savesIndependentModePerSelectedTool() {
        final ConferenceRepository repository = mock(ConferenceRepository.class);
        final Conference conference = new Conference("evento", "Evento", "owner");
        when(repository.findByUuid(conference.getUuid())).thenReturn(Optional.of(conference));
        final var configs = List.of(
                new dev.rafex.insightbloom.users.domain.model.CanvasConfig("DRAWIO", "MODERATOR_ONLY"),
                new dev.rafex.insightbloom.users.domain.model.CanvasConfig("EXCALIDRAW", "MODERATOR_ONLY"),
                new dev.rafex.insightbloom.users.domain.model.CanvasConfig("ETHERPAD", "INDEPENDENT"));

        final var result = new SetCanvasConfigUseCase(repository)
                .execute(conference.getUuid(), "owner", configs);

        assertTrue(result.isPresent());
        assertEquals(configs, conference.getCanvasConfigs());
        assertEquals(3, conference.getCanvasConfigs().size());
        assertEquals(null, conference.getCanvasTool());
        verify(repository).replaceCanvasConfigs(conference.getUuid(), configs);
    }

    @Test
    void rejectsUnknownTool() {
        final ConferenceRepository repository = mock(ConferenceRepository.class);

        assertThrows(IllegalArgumentException.class, () ->
                new SetCanvasConfigUseCase(repository)
                        .execute("conference", "owner", "UNKNOWN", "INDEPENDENT"));
    }

    @Test
    void acceptsCollaborativeModeOnlyForEtherpad() {
        final ConferenceRepository repository = mock(ConferenceRepository.class);
        final Conference conference = new Conference("evento", "Evento", "owner");
        when(repository.findByUuid(conference.getUuid())).thenReturn(Optional.of(conference));

        final var result = new SetCanvasConfigUseCase(repository)
                .execute(conference.getUuid(), "owner", "ETHERPAD", "COLLABORATIVE");

        assertTrue(result.isPresent());
        assertEquals("COLLABORATIVE", conference.getCanvasAudienceMode());
        assertThrows(IllegalArgumentException.class, () ->
                new SetCanvasConfigUseCase(repository)
                        .execute(conference.getUuid(), "owner", "DRAWIO", "COLLABORATIVE"));
    }

    @Test
    void acceptsModeratorOnlyModeForEtherpad() {
        final Conference conference = new Conference("id", "name", "owner", null, null, null);
        final ConferenceRepository repository = mock(ConferenceRepository.class);
        when(repository.findByUuid("conference"))
                .thenReturn(Optional.of(conference));

        assertDoesNotThrow(() ->
                new SetCanvasConfigUseCase(repository)
                        .execute("conference", "owner", "ETHERPAD", "MODERATOR_ONLY"));
    }
}
