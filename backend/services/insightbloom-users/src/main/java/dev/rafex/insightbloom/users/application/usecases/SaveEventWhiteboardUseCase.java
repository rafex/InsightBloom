package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.CanvasAudienceMode;
import dev.rafex.insightbloom.users.domain.model.CanvasConfig;
import dev.rafex.insightbloom.users.domain.model.CanvasTool;
import dev.rafex.insightbloom.users.domain.ports.ConferenceRepository;

/** Persiste exclusivamente el material de Excalidraw del moderador. */
public class SaveEventWhiteboardUseCase {
    private final ConferenceRepository conferenceRepository;

    public SaveEventWhiteboardUseCase(final ConferenceRepository conferenceRepository) {
        this.conferenceRepository = conferenceRepository;
    }

    public boolean execute(final String conferenceUuid, final String sceneJson, final String publishedSvg,
                           final String requestingUserUuid) {
        return conferenceRepository.findByUuid(conferenceUuid).map(conference -> {
            final CanvasAudienceMode audienceMode = conference.getCanvasConfigs().stream()
                    .filter(config -> CanvasTool.EXCALIDRAW.equals(config.tool()))
                    .map(CanvasConfig::audienceMode)
                    .findFirst()
                    .orElse(conference.getCanvasAudienceMode());
            if (requestingUserUuid != null
                    && (CanvasAudienceMode.INDEPENDENT.equals(audienceMode) || CanvasAudienceMode.MODERATOR_ONLY.equals(audienceMode))
                    && !conference.getCreatedByUserUuid().equals(requestingUserUuid)) {
                return false;
            }
            conference.setWhiteboardSceneAndPublishedSvg(sceneJson, publishedSvg);
            conferenceRepository.save(conference);
            return true;
        }).orElse(false);
    }
}
