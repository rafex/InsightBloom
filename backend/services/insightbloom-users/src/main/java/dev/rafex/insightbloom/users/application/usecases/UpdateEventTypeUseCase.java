package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.EventCapability;
import dev.rafex.insightbloom.users.domain.model.EventType;
import dev.rafex.insightbloom.users.domain.ports.EventTypeRepository;

import java.util.Set;

public class UpdateEventTypeUseCase {
    private final EventTypeRepository eventTypeRepository;

    public UpdateEventTypeUseCase(final EventTypeRepository eventTypeRepository) {
        this.eventTypeRepository = eventTypeRepository;
    }

    public EventType execute(final String uuid, final String name, final String description,
                              final Set<EventCapability> capabilities) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name_required");
        final EventType eventType = eventTypeRepository.findByUuid(uuid)
                .orElseThrow(() -> new IllegalArgumentException("event_type_not_found"));
        eventType.update(name, description, capabilities != null ? capabilities : Set.of());
        eventTypeRepository.save(eventType);
        return eventType;
    }
}
