package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.CanvasConfig;
import dev.rafex.insightbloom.users.domain.model.Conference;
import dev.rafex.insightbloom.users.domain.ports.ConferenceRepository;
import dev.rafex.insightbloom.users.domain.ports.EtherpadPort;
import dev.rafex.insightbloom.users.domain.services.EventCapabilityGuard;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EventMaterialsDownloadUseCaseTest {
    private static final String SVG = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"20\" height=\"20\"><rect width=\"20\" height=\"20\"/></svg>";

    @Test
    void includesGroupNotesAndPublishedNativeAndPngExportsButNeverPrivateNotes() {
        final ConferenceRepository repository = mock(ConferenceRepository.class);
        final EtherpadPort etherpad = mock(EtherpadPort.class);
        final EventCapabilityGuard capabilityGuard = mock(EventCapabilityGuard.class);
        final Conference conference = new Conference("evento", "Evento", "owner");
        conference.setCanvasConfigs(java.util.List.of(
                new CanvasConfig("DRAWIO", "MODERATOR_ONLY"),
                new CanvasConfig("EXCALIDRAW", "MODERATOR_ONLY"),
                new CanvasConfig("ETHERPAD", "COLLABORATIVE")));
        conference.setDiagramXmlAndPublishedSvg("<mxGraphModel/>", SVG);
        conference.setWhiteboardSceneAndPublishedSvg("{\"elements\":[]}", SVG);
        when(repository.findByUuid(conference.getUuid())).thenReturn(Optional.of(conference));
        when(etherpad.readPad(conference.getUuid())).thenReturn(new EtherpadPort.PadContent("group notes", "<p>group notes</p>"));

        final byte[] zip = new EventMaterialsDownloadUseCase(
                repository, capabilityGuard, new GetEventDiagramUseCase(repository),
                new GetEventWhiteboardUseCase(repository), etherpad).execute(conference.getUuid());

        final Set<String> entries = new HashSet<>();
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(zip), StandardCharsets.UTF_8)) {
            for (var entry = input.getNextEntry(); entry != null; entry = input.getNextEntry()) entries.add(entry.getName());
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        assertTrue(entries.contains("moderator/drawio/source.drawio"));
        assertTrue(entries.contains("moderator/drawio/export.png"));
        assertTrue(entries.contains("moderator/excalidraw/source.excalidraw"));
        assertTrue(entries.contains("moderator/excalidraw/export.png"));
        assertTrue(entries.contains("moderator/etherpad/export.txt"));
        assertTrue(entries.contains("manifest.json"));
        assertFalse(entries.stream().anyMatch(name -> name.contains("private")));
    }

    @Test
    void failsInsteadOfReturningAnIncompleteZipWhenGroupNotesCannotBeRead() {
        final ConferenceRepository repository = mock(ConferenceRepository.class);
        final EtherpadPort etherpad = mock(EtherpadPort.class);
        final EventCapabilityGuard capabilityGuard = mock(EventCapabilityGuard.class);
        final Conference conference = new Conference("evento", "Evento", "owner");
        conference.setCanvasConfigs(java.util.List.of(new CanvasConfig("ETHERPAD", "COLLABORATIVE")));
        when(repository.findByUuid(conference.getUuid())).thenReturn(Optional.of(conference));
        when(etherpad.readPad(conference.getUuid())).thenThrow(new IllegalStateException("gateway_unauthorized"));

        assertThrows(IllegalStateException.class, () -> new EventMaterialsDownloadUseCase(
                repository, capabilityGuard, new GetEventDiagramUseCase(repository),
                new GetEventWhiteboardUseCase(repository), etherpad).execute(conference.getUuid()));
    }
}
