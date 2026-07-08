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
        reservationRepository.delete(reservation.get().getUuid());
        if (reservation.get().getSeatUuid() == null) {
            conferenceRepository.decrementReservedCount(conferenceUuid);
        }
        return true;
    }
}
