package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.ports.ConferenceRepository;
import dev.rafex.insightbloom.users.domain.ports.DownloadEventRepository;
import dev.rafex.insightbloom.users.domain.ports.ReservationRepository;
import dev.rafex.insightbloom.users.domain.ports.TicketRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Eventos en los que un usuario está inscrito, con el nombre del evento resuelto.
 *
 * Dos mecanismos de inscripción coexisten en el dominio: {@link ReservationRepository} (boleto
 * "general" emitido por JoinConferenceUseCase/ReserveGeneralUseCase, incluso en modo NONE) y
 * {@link TicketRepository} (boleto con QR reclamado vía TicketUseCase.claim, usado en eventos
 * ticketed). Un usuario puede tener solo uno de los dos según cómo se unió al evento -- se
 * combinan ambas fuentes por conferenceUuid para no perder inscripciones que solo existen como
 * Ticket reclamado y nunca generaron Reservation.
 */
public class ListUserReservationsUseCase {
    private final ReservationRepository reservationRepository;
    private final TicketRepository ticketRepository;
    private final ConferenceRepository conferenceRepository;
    private final DownloadEventRepository downloadEventRepository;

    public ListUserReservationsUseCase(final ReservationRepository reservationRepository,
                                        final TicketRepository ticketRepository,
                                        final ConferenceRepository conferenceRepository,
                                        final DownloadEventRepository downloadEventRepository) {
        this.reservationRepository = reservationRepository;
        this.ticketRepository = ticketRepository;
        this.conferenceRepository = conferenceRepository;
        this.downloadEventRepository = downloadEventRepository;
    }

    public record Entry(String conferenceUuid, String conferenceName, String friendlyId, String status,
                         String createdAt, boolean certificateDownloaded) {}

    public List<Entry> execute(final String userUuid) {
        final Map<String, Entry> byConference = new LinkedHashMap<>();
        reservationRepository.findByUser(userUuid).forEach(r -> byConference.put(r.getConferenceUuid(),
                entry(r.getConferenceUuid(), r.getStatus().name(), r.getCreatedAt().toString(), userUuid)));
        ticketRepository.findByClaimedUser(userUuid).stream()
                .filter(t -> !byConference.containsKey(t.getConferenceUuid()))
                .forEach(t -> byConference.put(t.getConferenceUuid(),
                        entry(t.getConferenceUuid(), t.getStatus().name(), t.getClaimedAt().toString(), userUuid)));
        return List.copyOf(byConference.values());
    }

    private Entry entry(final String conferenceUuid, final String status, final String createdAt,
                        final String userUuid) {
        final var conference = conferenceRepository.findByUuid(conferenceUuid).orElse(null);
        return new Entry(
                conferenceUuid,
                conference != null ? conference.getName() : null,
                conference != null ? conference.getFriendlyId() : null,
                status,
                createdAt,
                downloadEventRepository.existsByConferenceAndUserAndKind(conferenceUuid, userUuid, "certificate"));
    }
}
