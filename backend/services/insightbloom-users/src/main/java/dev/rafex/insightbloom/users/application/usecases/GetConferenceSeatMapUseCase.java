package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.ports.ReservationRepository;
import dev.rafex.insightbloom.users.domain.ports.VenueSeatRepository;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Mapa de asientos con su estado de ocupación, sin exponer quién ocupa cada uno. */
public class GetConferenceSeatMapUseCase {
    private final VenueSeatRepository venueSeatRepository;
    private final ReservationRepository reservationRepository;

    public GetConferenceSeatMapUseCase(final VenueSeatRepository venueSeatRepository,
                                        final ReservationRepository reservationRepository) {
        this.venueSeatRepository = venueSeatRepository;
        this.reservationRepository = reservationRepository;
    }

    public record SeatView(String uuid, String label, double x, double y, boolean occupied) {}

    public List<SeatView> execute(final String conferenceUuid) {
        final Set<String> occupiedSeatUuids = reservationRepository.findByConference(conferenceUuid).stream()
                .map(dev.rafex.insightbloom.users.domain.model.Reservation::getSeatUuid)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        return venueSeatRepository.findByConference(conferenceUuid).stream()
                .map(s -> new SeatView(s.getUuid(), s.getLabel(), s.getX(), s.getY(),
                        occupiedSeatUuids.contains(s.getUuid())))
                .toList();
    }
}
