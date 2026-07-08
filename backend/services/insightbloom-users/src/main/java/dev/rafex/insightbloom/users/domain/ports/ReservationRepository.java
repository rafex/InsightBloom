package dev.rafex.insightbloom.users.domain.ports;

import dev.rafex.insightbloom.users.domain.model.Reservation;

import java.util.List;
import java.util.Optional;

public interface ReservationRepository {
    /** @throws RuntimeException si viola UNIQUE(conference_uuid, seat_uuid) (asiento ya tomado). */
    void save(Reservation reservation);
    void delete(String uuid);
    Optional<Reservation> findByUuid(String uuid);
    Optional<Reservation> findByTicketCode(String conferenceUuid, String ticketCode);
    Optional<Reservation> findByConferenceAndUser(String conferenceUuid, String userUuid);
    List<Reservation> findByConference(String conferenceUuid);
}
