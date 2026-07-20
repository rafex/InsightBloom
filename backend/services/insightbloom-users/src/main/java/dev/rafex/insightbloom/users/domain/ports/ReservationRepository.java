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
    /** @return true si efectivamente borro una fila (false si ya no existia -- ver CancelReservationUseCase). */
    boolean delete(String uuid);
    /**
     * Transicion atomica a CHECKED_IN: UPDATE condicionado a que el status actual NO sea ya
     * CHECKED_IN, para que dos escaneos concurrentes del mismo QR (dos puertas, doble tap) no
     * ambos "ganen" la carrera con el patron read-compare-write en Java.
     * @return true si esta llamada fue la que efectivamente hizo la transicion.
     */
    boolean tryCheckIn(String uuid, String checkedInAt);
    Optional<Reservation> findByUuid(String uuid);
    Optional<Reservation> findByTicketCode(String conferenceUuid, String ticketCode);
    Optional<Reservation> findByConferenceAndUser(String conferenceUuid, String userUuid);
    List<Reservation> findByConference(String conferenceUuid);
    /** Todas las reservas de un usuario, en cualquier conferencia. */
    List<Reservation> findByUser(String userUuid);
}
