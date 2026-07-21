package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.Conference;
import dev.rafex.insightbloom.users.domain.ports.ConferenceRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

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
    }

    @Test
    void rejectsUnknownTool() {
        final ConferenceRepository repository = mock(ConferenceRepository.class);

        assertThrows(IllegalArgumentException.class, () ->
                new SetCanvasConfigUseCase(repository)
                        .execute("conference", "owner", "UNKNOWN", "INDEPENDENT"));
    }
}
