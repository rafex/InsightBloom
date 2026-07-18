package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.Conference;
import dev.rafex.insightbloom.users.domain.ports.ConferenceRepository;
import dev.rafex.insightbloom.users.domain.ports.ReservationRepository;

import java.util.Optional;
import java.util.Set;

/**
 * Fija el modo de reserva de una conferencia (NONE, GENERAL o SEATED) y su aforo. El aforo ya
 * no es exclusivo de GENERAL -- aplica siempre (DEC 2026-07-18): la infraestructura tiene
 * recursos finitos, así que todo evento declara cuánta gente va a tener acceso, sin excepción
 * de "NONE = sin límite". Si no se manda un valor válido (&gt;=1), se usa el default (10).
 */
public class SetSeatingModeUseCase {
    private static final Set<String> VALID_MODES = Set.of("NONE", "GENERAL", "SEATED");
    private static final int DEFAULT_CAPACITY = 10;

    private final ConferenceRepository conferenceRepository;
    private final ReservationRepository reservationRepository;

    public SetSeatingModeUseCase(final ConferenceRepository conferenceRepository,
                                  final ReservationRepository reservationRepository) {
        this.conferenceRepository = conferenceRepository;
        this.reservationRepository = reservationRepository;
    }

    public Optional<Conference> execute(final String conferenceUuid, final String requestingUserUuid,
                                         final String seatingMode, final Integer capacity) {
        if (seatingMode == null || !VALID_MODES.contains(seatingMode)) {
            throw new IllegalArgumentException("invalid_seating_mode");
        }
        return conferenceRepository.findByUuid(conferenceUuid)
                .filter(c -> c.getCreatedByUserUuid().equals(requestingUserUuid))
                .map(c -> {
                    if ("SEATED".equals(c.getSeatingMode()) && !"SEATED".equals(seatingMode)) {
                        final boolean hasSeatedReservations = reservationRepository.findByConference(conferenceUuid)
                                .stream().anyMatch(r -> r.getSeatUuid() != null);
                        if (hasSeatedReservations) {
                            throw new IllegalStateException("seated_reservations_active");
                        }
                    }
                    c.setSeatingMode(seatingMode);
                    c.setCapacity(capacity != null && capacity >= 1 ? capacity : DEFAULT_CAPACITY);
                    conferenceRepository.save(c);
                    return c;
                });
    }
}
