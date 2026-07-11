package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.EventType;
import dev.rafex.insightbloom.users.domain.ports.EventTypeRepository;

public class SetEventTypeActiveUseCase {
    private final EventTypeRepository eventTypeRepository;

    public SetEventTypeActiveUseCase(final EventTypeRepository eventTypeRepository) {
        this.eventTypeRepository = eventTypeRepository;
    }

    public EventType execute(final String uuid, final boolean active) {
        final EventType eventType = eventTypeRepository.findByUuid(uuid)
                .orElseThrow(() -> new IllegalArgumentException("event_type_not_found"));
        eventType.setActive(active);
        eventTypeRepository.save(eventType);
        return eventType;
    }
}
