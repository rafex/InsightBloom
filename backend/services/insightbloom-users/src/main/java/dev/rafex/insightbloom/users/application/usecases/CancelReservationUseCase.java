package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.ports.ConferenceRepository;
import dev.rafex.insightbloom.users.domain.ports.ReservationRepository;

public class CancelReservationUseCase {
    private final ReservationRepository reservationRepository;
    private final ConferenceRepository conferenceRepository;

    public CancelReservationUseCase(final ReservationRepository reservationRepository,
                                     final ConferenceRepository conferenceRepository) {
        this.reservationRepository = reservationRepository;
        this.conferenceRepository = conferenceRepository;
    }

    public boolean execute(final String conferenceUuid, final String userUuid) {
        final var reservation = reservationRepository.findByConferenceAndUser(conferenceUuid, userUuid);
        if (reservation.isEmpty()) return false;
        final boolean deleted = reservationRepository.delete(reservation.get().getUuid());
        if (!deleted) return false;
        // Todo boleto (GENERAL, NONE o SEATED) cuenta contra el aforo del evento desde el
        // 2026-07-18 -- cancelar siempre libera el cupo, sin importar el modo. El decremento solo
        // ocurre si el delete de arriba realmente borro una fila (evita doble-decremento si dos
        // cancelaciones concurrentes de la misma reserva compiten).
        conferenceRepository.decrementReservedCount(conferenceUuid);
        return true;
    }
}
