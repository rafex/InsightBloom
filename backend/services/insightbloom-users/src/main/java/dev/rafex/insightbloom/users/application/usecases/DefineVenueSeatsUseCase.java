package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.VenueSeat;
import dev.rafex.insightbloom.users.domain.ports.ConferenceRepository;
import dev.rafex.insightbloom.users.domain.ports.ReservationRepository;
import dev.rafex.insightbloom.users.domain.ports.VenueSeatRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Reemplazo completo del mapa de asientos de una conferencia (organizer-only). */
public class DefineVenueSeatsUseCase {
    private final ConferenceRepository conferenceRepository;
    private final VenueSeatRepository venueSeatRepository;
    private final ReservationRepository reservationRepository;

    public DefineVenueSeatsUseCase(final ConferenceRepository conferenceRepository,
                                    final VenueSeatRepository venueSeatRepository,
                                    final ReservationRepository reservationRepository) {
        this.conferenceRepository = conferenceRepository;
        this.venueSeatRepository = venueSeatRepository;
        this.reservationRepository = reservationRepository;
    }

    public record SeatInput(String uuid, String label, double x, double y) {}

    public Optional<List<VenueSeat>> execute(final String conferenceUuid, final String requestingUserUuid,
                                              final List<SeatInput> seats) {
        return conferenceRepository.findByUuid(conferenceUuid)
                .filter(c -> c.getCreatedByUserUuid().equals(requestingUserUuid))
                .map(c -> {
                    final Set<String> keptUuids = seats.stream()
                            .map(SeatInput::uuid).filter(java.util.Objects::nonNull).collect(Collectors.toSet());
                    final Set<String> reservedSeatUuids = reservationRepository.findByConference(conferenceUuid)
                            .stream().map(dev.rafex.insightbloom.users.domain.model.Reservation::getSeatUuid)
                            .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
                    final boolean removesReservedSeat = venueSeatRepository.findByConference(conferenceUuid).stream()
                            .map(VenueSeat::getUuid)
                            .anyMatch(uuid -> reservedSeatUuids.contains(uuid) && !keptUuids.contains(uuid));
                    if (removesReservedSeat) {
                        throw new IllegalStateException("seat_has_active_reservation");
                    }
                    venueSeatRepository.deleteByConference(conferenceUuid);
                    final List<VenueSeat> result = seats.stream().map(s -> {
                        final String uuid = s.uuid() != null ? s.uuid() : UUID.randomUUID().toString();
                        final VenueSeat seat = new VenueSeat(uuid, conferenceUuid, s.label(), s.x(), s.y(), Instant.now());
                        venueSeatRepository.save(seat);
                        return seat;
                    }).toList();
                    return result;
                });
    }
}
