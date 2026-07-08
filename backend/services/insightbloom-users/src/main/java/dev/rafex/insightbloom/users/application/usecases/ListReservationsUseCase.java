package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.Reservation;
import dev.rafex.insightbloom.users.domain.ports.ConferenceRepository;
import dev.rafex.insightbloom.users.domain.ports.ReservationRepository;

import java.util.List;
import java.util.Optional;

/** Lista de boletos de una conferencia, para el tablero de check-in del organizador. */
public class ListReservationsUseCase {
    private final ConferenceRepository conferenceRepository;
    private final ReservationRepository reservationRepository;

    public ListReservationsUseCase(final ConferenceRepository conferenceRepository,
                                    final ReservationRepository reservationRepository) {
        this.conferenceRepository = conferenceRepository;
        this.reservationRepository = reservationRepository;
    }

    public Optional<List<Reservation>> execute(final String conferenceUuid, final String requestingUserUuid) {
        return conferenceRepository.findByUuid(conferenceUuid)
                .filter(c -> c.getCreatedByUserUuid().equals(requestingUserUuid))
                .map(c -> reservationRepository.findByConference(conferenceUuid));
    }
}
