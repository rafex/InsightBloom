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

class SaveEventDiagramUseCaseTest {
    private ConferenceRepository conferenceRepository;
    private SaveEventDiagramUseCase useCase;
    private Conference conference;

    @BeforeEach
    void setUp() {
        conferenceRepository = Mockito.mock(ConferenceRepository.class);
        useCase = new SaveEventDiagramUseCase(conferenceRepository);
        conference = new Conference("demo-evento", "Demo evento", "moderator-1");
        conference.setCanvasConfigs(List.of(new CanvasConfig("DRAWIO", "MODERATOR_ONLY")));
        Mockito.when(conferenceRepository.findByUuid(conference.getUuid())).thenReturn(Optional.of(conference));
    }

    @Test
    void moderatorPublishesNativeSourceAndSvgWithVersion() {
        final boolean saved = useCase.execute(conference.getUuid(), "<mxGraphModel/>",
                "data:image/svg+xml;base64,AAA", "moderator-1");

        assertTrue(saved);
        assertEquals("<mxGraphModel/>", conference.getDiagramXml());
        assertEquals("data:image/svg+xml;base64,AAA", conference.getDiagramPublishedSvg());
        assertEquals(1, conference.getDiagramVersion());
        assertNotNull(conference.getDiagramUpdatedAt());
        Mockito.verify(conferenceRepository).save(conference);
    }

    @Test
    void attendeeCannotPublishModeratorMaterial() {
        final boolean saved = useCase.execute(conference.getUuid(), "<mxGraphModel/>",
                "data:image/svg+xml;base64,AAA", "attendee-1");

        assertFalse(saved);
        assertNull(conference.getDiagramXml());
        assertEquals(0, conference.getDiagramVersion());
        Mockito.verify(conferenceRepository, Mockito.never()).save(Mockito.any());
    }
}
