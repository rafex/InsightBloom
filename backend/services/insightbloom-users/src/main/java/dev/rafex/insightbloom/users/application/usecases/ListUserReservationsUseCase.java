package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.ports.ConferenceRepository;
import dev.rafex.insightbloom.users.domain.ports.DownloadEventRepository;
import dev.rafex.insightbloom.users.domain.ports.ReservationRepository;

import java.util.List;

/** Eventos en los que un usuario tiene una reserva, con el nombre del evento resuelto. */
public class ListUserReservationsUseCase {
    private final ReservationRepository reservationRepository;
    private final ConferenceRepository conferenceRepository;
    private final DownloadEventRepository downloadEventRepository;

    public ListUserReservationsUseCase(final ReservationRepository reservationRepository,
                                        final ConferenceRepository conferenceRepository,
                                        final DownloadEventRepository downloadEventRepository) {
        this.reservationRepository = reservationRepository;
        this.conferenceRepository = conferenceRepository;
        this.downloadEventRepository = downloadEventRepository;
    }

    public record Entry(String conferenceUuid, String conferenceName, String friendlyId, String status,
                         String createdAt, boolean certificateDownloaded) {}

    public List<Entry> execute(final String userUuid) {
        return reservationRepository.findByUser(userUuid).stream()
                .map(r -> {
                    final var conference = conferenceRepository.findByUuid(r.getConferenceUuid()).orElse(null);
                    return new Entry(
                            r.getConferenceUuid(),
                            conference != null ? conference.getName() : null,
                            conference != null ? conference.getFriendlyId() : null,
                            r.getStatus().name(),
                            r.getCreatedAt().toString(),
                            downloadEventRepository.existsByConferenceAndUserAndKind(
                                    r.getConferenceUuid(), userUuid, "certificate"));
                })
                .toList();
    }
}
