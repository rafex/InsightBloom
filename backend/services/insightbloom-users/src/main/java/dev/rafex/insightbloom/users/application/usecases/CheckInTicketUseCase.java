package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.Reservation;
import dev.rafex.insightbloom.users.domain.ports.ReservationRepository;

import java.time.Instant;

/** Marca un boleto como ingresado tras escanear su QR en la puerta. */
public class CheckInTicketUseCase {
    private final ReservationRepository reservationRepository;

    public CheckInTicketUseCase(final ReservationRepository reservationRepository) {
        this.reservationRepository = reservationRepository;
    }

    public Reservation execute(final String conferenceUuid, final String ticketCode) {
        final Reservation reservation = reservationRepository.findByTicketCode(conferenceUuid, ticketCode)
                .orElseThrow(() -> new IllegalArgumentException("ticket_not_found"));
        // UPDATE atomico condicionado a status != CHECKED_IN (no read-compare-write en Java): dos
        // escaneos concurrentes del mismo QR (dos puertas) no pueden ambos "ganar" la carrera.
        final String checkedInAt = Instant.now().toString();
        final boolean didCheckIn = reservationRepository.tryCheckIn(reservation.getUuid(), checkedInAt);
        if (!didCheckIn) {
            throw new IllegalStateException("already_checked_in");
        }
        return reservationRepository.findByUuid(reservation.getUuid()).orElse(reservation);
    }
}
