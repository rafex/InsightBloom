package dev.rafex.insightbloom.users.domain.services;

import dev.rafex.insightbloom.users.domain.model.Conference;
import dev.rafex.insightbloom.users.domain.model.EventCapability;
import dev.rafex.insightbloom.users.domain.ports.EventTypeRepository;

/**
 * Resuelve el tipo de evento de una conferencia y expone si tiene activa una capacidad dada, para
 * gatear rutas HTTP sensibles sin comparar contra un nombre de tipo hardcodeado. Una conferencia
 * cuyo tipo no existe (dato corrupto o tipo borrado) se trata como sin capacidades, no como error.
 */
public class EventCapabilityGuard {
    private final EventTypeRepository eventTypeRepository;

    public EventCapabilityGuard(final EventTypeRepository eventTypeRepository) {
        this.eventTypeRepository = eventTypeRepository;
    }

    public boolean hasCapability(final Conference conference, final EventCapability capability) {
        return eventTypeRepository.findByKey(conference.getEventTypeKey())
                .map(eventType -> eventType.hasCapability(capability))
                .orElse(false);
    }
}
