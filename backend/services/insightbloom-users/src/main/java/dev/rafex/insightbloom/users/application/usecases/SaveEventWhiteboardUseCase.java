package dev.rafex.insightbloom.users.application.usecases;

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
            final String audienceMode = conference.getCanvasConfigs().stream()
                    .filter(config -> "EXCALIDRAW".equals(config.tool()))
                    .map(dev.rafex.insightbloom.users.domain.model.CanvasConfig::audienceMode)
                    .findFirst()
                    .orElse(conference.getCanvasAudienceMode());
            if (requestingUserUuid != null
                    && ("INDEPENDENT".equals(audienceMode) || "MODERATOR_ONLY".equals(audienceMode))
                    && !conference.getCreatedByUserUuid().equals(requestingUserUuid)) {
                return false;
            }
            conference.setWhiteboardSceneAndPublishedSvg(sceneJson, publishedSvg);
            conferenceRepository.save(conference);
            return true;
        }).orElse(false);
    }
}
