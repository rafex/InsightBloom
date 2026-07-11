package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.Conference;
import dev.rafex.insightbloom.users.domain.ports.ConferenceRepository;
import dev.rafex.insightbloom.users.domain.ports.EtherpadPort;

import java.util.Optional;

/** Crea (de forma perezosa e idempotente) el pad de Etherpad de un evento en su primer acceso. */
public class GetOrCreateEventPadUseCase {
    private final ConferenceRepository conferenceRepository;
    private final EtherpadPort etherpadPort;

    public GetOrCreateEventPadUseCase(final ConferenceRepository conferenceRepository,
                                       final EtherpadPort etherpadPort) {
        this.conferenceRepository = conferenceRepository;
        this.etherpadPort = etherpadPort;
    }

    public record PadInfo(String padId) {}

    public Optional<PadInfo> execute(final String conferenceUuid) {
        return conferenceRepository.findByUuid(conferenceUuid).map(this::ensurePad);
    }

    private PadInfo ensurePad(final Conference conference) {
        etherpadPort.ensurePadExists(conference.getUuid());
        return new PadInfo(conference.getUuid());
    }
}
