package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.ports.ConferenceRepository;

import java.time.Instant;
import java.util.Optional;

/** Devuelve el ultimo XML de drawio guardado para un evento (o vacio si nunca se guardo). */
public class GetEventDiagramUseCase {
    private final ConferenceRepository conferenceRepository;

    public GetEventDiagramUseCase(final ConferenceRepository conferenceRepository) {
        this.conferenceRepository = conferenceRepository;
    }

    public record DiagramInfo(String xml, String publishedSvg, Instant updatedAt, long version) {}

    public Optional<DiagramInfo> execute(final String conferenceUuid) {
        return conferenceRepository.findByUuid(conferenceUuid)
                .map(c -> new DiagramInfo(c.getDiagramXml() != null ? c.getDiagramXml() : "",
                        c.getDiagramPublishedSvg(), c.getDiagramUpdatedAt(), c.getDiagramVersion()));
    }
}
