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
        // El boleto sin asiento se emite tanto en modo GENERAL (cuenta contra el aforo) como en
        // modo NONE (comprobante de acceso simple, sin aforo) -- solo GENERAL debe liberar cupo.
        final boolean isGeneralAdmission = reservation.get().getSeatUuid() == null
                && conferenceRepository.findByUuid(conferenceUuid)
                        .map(c -> "GENERAL".equals(c.getSeatingMode()))
                        .orElse(false);
        if (isGeneralAdmission) {
            conferenceRepository.decrementReservedCount(conferenceUuid);
        }
        return true;
    }
}
