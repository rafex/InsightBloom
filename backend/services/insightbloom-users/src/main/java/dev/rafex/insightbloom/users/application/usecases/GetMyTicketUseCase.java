package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.Reservation;
import dev.rafex.insightbloom.users.domain.ports.ReservationRepository;

import java.util.Optional;

public class GetMyTicketUseCase {
    private final ReservationRepository reservationRepository;

    public GetMyTicketUseCase(final ReservationRepository reservationRepository) {
        this.reservationRepository = reservationRepository;
    }

    public Optional<Reservation> execute(final String conferenceUuid, final String userUuid) {
        return reservationRepository.findByConferenceAndUser(conferenceUuid, userUuid);
    }
}
