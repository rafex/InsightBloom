package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.Conference;
import dev.rafex.insightbloom.users.domain.ports.ConferenceRepository;
import dev.rafex.insightbloom.users.domain.ports.ReservationRepository;

import java.util.Optional;
import java.util.Set;

/** Fija el modo de reserva de una conferencia (NONE, GENERAL o SEATED) y su aforo si aplica. */
public class SetSeatingModeUseCase {
    private static final Set<String> VALID_MODES = Set.of("NONE", "GENERAL", "SEATED");

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
                    c.setCapacity("GENERAL".equals(seatingMode) ? capacity : null);
                    conferenceRepository.save(c);
                    return c;
                });
    }
}
