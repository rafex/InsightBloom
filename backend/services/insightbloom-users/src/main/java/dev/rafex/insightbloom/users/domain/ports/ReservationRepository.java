package dev.rafex.insightbloom.users.domain.ports;

import dev.rafex.insightbloom.users.domain.model.Reservation;

import java.util.List;
import java.util.Optional;

public interface ReservationRepository {
    void save(Reservation reservation);
    /**
     * INSERT puro (no reemplaza filas existentes) para creación de reservas nuevas: permite
     * detectar la violación de UNIQUE(conference_uuid, seat_uuid) cuando dos reservas concurrentes
     * compiten por el mismo asiento.
     * @throws RuntimeException si viola una restricción UNIQUE (asiento ya tomado).
     */
    void insertNew(Reservation reservation);
    void delete(String uuid);
    Optional<Reservation> findByUuid(String uuid);
    Optional<Reservation> findByTicketCode(String conferenceUuid, String ticketCode);
    Optional<Reservation> findByConferenceAndUser(String conferenceUuid, String userUuid);
    List<Reservation> findByConference(String conferenceUuid);
}
