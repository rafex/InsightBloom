package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.Conference;
import dev.rafex.insightbloom.users.domain.model.EventCapability;
import dev.rafex.insightbloom.users.domain.ports.ConferenceRepository;
import dev.rafex.insightbloom.users.domain.ports.EventTypeRepository;
import dev.rafex.insightbloom.users.domain.ports.ReservationRepository;

import java.util.Optional;

/**
 * Cambia el tipo de evento de una conferencia ya creada. Bloquea el cambio si dejaría datos
 * activos huérfanos (reservas de asiento sin capacidad TICKETING_SEATED en el tipo destino),
 * mismo criterio que {@link SetSeatingModeUseCase} usa para no permitir salir de SEATED con
 * reservas de asiento activas.
 */
public class SetEventTypeUseCase {
    private final ConferenceRepository conferenceRepository;
    private final EventTypeRepository eventTypeRepository;
    private final ReservationRepository reservationRepository;

    public SetEventTypeUseCase(final ConferenceRepository conferenceRepository,
                                final EventTypeRepository eventTypeRepository,
                                final ReservationRepository reservationRepository) {
        this.conferenceRepository = conferenceRepository;
        this.eventTypeRepository = eventTypeRepository;
        this.reservationRepository = reservationRepository;
    }

    public Optional<Conference> execute(final String conferenceUuid, final String requestingUserUuid,
                                         final String eventTypeKey) {
        final var targetType = eventTypeRepository.findByKey(eventTypeKey)
                .orElseThrow(() -> new IllegalArgumentException("event_type_not_found"));
        if (!targetType.isActive()) throw new IllegalArgumentException("event_type_inactive");

        return conferenceRepository.findByUuid(conferenceUuid)
                .filter(c -> c.getCreatedByUserUuid().equals(requestingUserUuid))
                .map(c -> {
                    if ("SEATED".equals(c.getSeatingMode()) && !targetType.hasCapability(EventCapability.TICKETING_SEATED)) {
                        final boolean hasSeatedReservations = reservationRepository.findByConference(conferenceUuid)
                                .stream().anyMatch(r -> r.getSeatUuid() != null);
                        if (hasSeatedReservations) {
                            throw new IllegalStateException("seated_reservations_active");
                        }
                    }
                    c.setEventTypeKey(eventTypeKey);
                    conferenceRepository.save(c);
                    return c;
                });
    }
}
