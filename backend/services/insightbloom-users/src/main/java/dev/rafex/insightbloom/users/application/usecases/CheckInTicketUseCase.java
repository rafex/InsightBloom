package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.Reservation;
import dev.rafex.insightbloom.users.domain.model.ReservationStatus;
import dev.rafex.insightbloom.users.domain.ports.ReservationRepository;

/** Marca un boleto como ingresado tras escanear su QR en la puerta. */
public class CheckInTicketUseCase {
    private final ReservationRepository reservationRepository;

    public CheckInTicketUseCase(final ReservationRepository reservationRepository) {
        this.reservationRepository = reservationRepository;
    }

    public Reservation execute(final String conferenceUuid, final String ticketCode) {
        final Reservation reservation = reservationRepository.findByTicketCode(conferenceUuid, ticketCode)
                .orElseThrow(() -> new IllegalArgumentException("ticket_not_found"));
        if (reservation.getStatus() == ReservationStatus.CHECKED_IN) {
            throw new IllegalStateException("already_checked_in");
        }
        reservation.markCheckedIn();
        reservationRepository.save(reservation);
        return reservation;
    }
}
