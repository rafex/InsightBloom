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
        return conferenceRepository.findByUuid(conferenceUuid).map(conference -> {
            conference.setDiagramXml(xml);
            conference.setDiagramPurgedAt(null);
            conferenceRepository.save(conference);
            return true;
        }).orElse(false);
    }
}
