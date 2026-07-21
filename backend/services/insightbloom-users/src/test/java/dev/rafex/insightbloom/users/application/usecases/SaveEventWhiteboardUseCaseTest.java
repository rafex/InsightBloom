package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.CanvasConfig;
import dev.rafex.insightbloom.users.domain.model.Conference;
import dev.rafex.insightbloom.users.domain.ports.ConferenceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SaveEventWhiteboardUseCaseTest {
    private ConferenceRepository conferenceRepository;
    private SaveEventWhiteboardUseCase useCase;
    private Conference conference;

    @BeforeEach
    void setUp() {
        conferenceRepository = Mockito.mock(ConferenceRepository.class);
        useCase = new SaveEventWhiteboardUseCase(conferenceRepository);
        conference = new Conference("demo-evento", "Demo evento", "moderator-1");
        conference.setCanvasConfigs(List.of(new CanvasConfig("EXCALIDRAW", "MODERATOR_ONLY")));
        Mockito.when(conferenceRepository.findByUuid(conference.getUuid())).thenReturn(Optional.of(conference));
    }

    @Test
    void moderatorPublishesNativeSceneAndSvgWithVersion() {
        final boolean saved = useCase.execute(conference.getUuid(),
                "{\"type\":\"excalidraw\",\"elements\":[]}",
                "data:image/svg+xml;base64,AAA", "moderator-1");

        assertTrue(saved);
        assertEquals("{\"type\":\"excalidraw\",\"elements\":[]}", conference.getWhiteboardSceneJson());
        assertEquals("data:image/svg+xml;base64,AAA", conference.getWhiteboardPublishedSvg());
        assertEquals(1, conference.getWhiteboardVersion());
        assertNotNull(conference.getWhiteboardUpdatedAt());
        Mockito.verify(conferenceRepository).save(conference);
    }

    @Test
    void attendeeCannotPublishModeratorMaterial() {
        final boolean saved = useCase.execute(conference.getUuid(), "{}",
                "data:image/svg+xml;base64,AAA", "attendee-1");

        assertFalse(saved);
        assertNull(conference.getWhiteboardSceneJson());
        assertEquals(0, conference.getWhiteboardVersion());
        Mockito.verify(conferenceRepository, Mockito.never()).save(Mockito.any());
    }
}
