package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.CanvasAudienceMode;
import dev.rafex.insightbloom.users.domain.model.CanvasConfig;
import dev.rafex.insightbloom.users.domain.model.CanvasTool;
import dev.rafex.insightbloom.users.domain.model.Conference;
import dev.rafex.insightbloom.users.domain.ports.ConferenceRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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
        assertEquals(CanvasTool.EXCALIDRAW, conference.getCanvasTool());
        assertEquals(CanvasAudienceMode.MODERATOR_ONLY, conference.getCanvasAudienceMode());
        verify(repository).save(conference);
        verify(repository).replaceCanvasConfigs(conference.getUuid(), conference.getCanvasConfigs());
    }

    @Test
    void savesIndependentModePerSelectedTool() {
        final ConferenceRepository repository = mock(ConferenceRepository.class);
        final Conference conference = new Conference("evento", "Evento", "owner");
        when(repository.findByUuid(conference.getUuid())).thenReturn(Optional.of(conference));
        final var configs = List.of(
                new CanvasConfig(CanvasTool.DRAWIO, CanvasAudienceMode.MODERATOR_ONLY),
                new CanvasConfig(CanvasTool.EXCALIDRAW, CanvasAudienceMode.MODERATOR_ONLY),
                new CanvasConfig(CanvasTool.ETHERPAD, CanvasAudienceMode.INDEPENDENT));

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
        assertEquals(CanvasAudienceMode.COLLABORATIVE, conference.getCanvasAudienceMode());
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
