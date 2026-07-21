package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.ports.ConferenceRepository;

import java.time.Instant;
import java.util.Optional;

/** Devuelve la escena nativa y el SVG publicado de la pizarra de Excalidraw. */
public class GetEventWhiteboardUseCase {
    private final ConferenceRepository conferenceRepository;

    public GetEventWhiteboardUseCase(final ConferenceRepository conferenceRepository) {
        this.conferenceRepository = conferenceRepository;
    }

    public record WhiteboardInfo(String sceneJson, String publishedSvg, Instant updatedAt, long version) {}

    public Optional<WhiteboardInfo> execute(final String conferenceUuid) {
        return conferenceRepository.findByUuid(conferenceUuid)
                .map(c -> new WhiteboardInfo(
                        c.getWhiteboardSceneJson() != null ? c.getWhiteboardSceneJson() : "",
                        c.getWhiteboardPublishedSvg(), c.getWhiteboardUpdatedAt(), c.getWhiteboardVersion()));
    }
}
