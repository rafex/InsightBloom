package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.EventType;
import dev.rafex.insightbloom.users.domain.ports.EventTypeRepository;

import java.util.List;

public class ListEventTypesUseCase {
    private final EventTypeRepository eventTypeRepository;

    public ListEventTypesUseCase(final EventTypeRepository eventTypeRepository) {
        this.eventTypeRepository = eventTypeRepository;
    }

    public List<EventType> execute(final boolean activeOnly) {
        return activeOnly ? eventTypeRepository.findActive() : eventTypeRepository.findAll();
    }
}
