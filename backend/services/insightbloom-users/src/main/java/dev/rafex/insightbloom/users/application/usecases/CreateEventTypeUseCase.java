package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.EventCapability;
import dev.rafex.insightbloom.users.domain.model.EventType;
import dev.rafex.insightbloom.users.domain.ports.EventTypeRepository;

import java.util.Set;

public class CreateEventTypeUseCase {
    private final EventTypeRepository eventTypeRepository;

    public CreateEventTypeUseCase(final EventTypeRepository eventTypeRepository) {
        this.eventTypeRepository = eventTypeRepository;
    }

    public EventType execute(final String key, final String name, final String description,
                              final Set<EventCapability> capabilities) {
        if (key == null || key.isBlank()) throw new IllegalArgumentException("key_required");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name_required");
        if (eventTypeRepository.existsByKey(key)) throw new IllegalArgumentException("key_already_exists");

        final EventType eventType = new EventType(key, name, description, capabilities != null ? capabilities : Set.of());
        eventTypeRepository.save(eventType);
        return eventType;
    }
}
