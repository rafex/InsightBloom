package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.Conference;
import dev.rafex.insightbloom.users.domain.ports.ConferenceRepository;

/**
 * Guarda temporalmente el ultimo XML del diagrama de drawio de un evento (para poder
 * descargarlo/distribuirlo despues), reemplazando el guardado anterior. Se purga por TTL
 * junto con el resto de datos efimeros del evento (ver DEC-0020 y PurgeExpiredEventDiagramsUseCase).
 */
public class SaveEventDiagramUseCase {
    private final ConferenceRepository conferenceRepository;

    public SaveEventDiagramUseCase(final ConferenceRepository conferenceRepository) {
        this.conferenceRepository = conferenceRepository;
    }

    public boolean execute(final String conferenceUuid, final String xml) {
        return execute(conferenceUuid, xml, null);
    }

    public boolean execute(final String conferenceUuid, final String xml, final String requestingUserUuid) {
        return conferenceRepository.findByUuid(conferenceUuid).map(conference -> {
            // En las modalidades nuevas solo el moderador/creador deja material persistente.
            // El overload anterior conserva compatibilidad con tareas internas existentes.
            final String audienceMode = conference.getCanvasConfigs().stream()
                    .filter(config -> "DRAWIO".equals(config.tool()))
                    .map(dev.rafex.insightbloom.users.domain.model.CanvasConfig::audienceMode)
                    .findFirst()
                    .orElse(conference.getCanvasAudienceMode());
            if (requestingUserUuid != null
                    && ("INDEPENDENT".equals(audienceMode)
                    || "MODERATOR_ONLY".equals(audienceMode))
                    && !conference.getCreatedByUserUuid().equals(requestingUserUuid)) {
                return false;
            }
            conference.setDiagramXml(xml);
            conference.setDiagramPurgedAt(null);
            conferenceRepository.save(conference);
            return true;
        }).orElse(false);
    }
}
