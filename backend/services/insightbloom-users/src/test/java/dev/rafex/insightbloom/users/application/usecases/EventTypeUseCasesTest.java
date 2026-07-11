package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.EventCapability;
import dev.rafex.insightbloom.users.domain.model.EventType;
import dev.rafex.insightbloom.users.domain.ports.EventTypeRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class EventTypeUseCasesTest {

    @Test
    void create_duplicateKey_throwsClearError() {
        final EventTypeRepository repo = Mockito.mock(EventTypeRepository.class);
        Mockito.when(repo.existsByKey("conference")).thenReturn(true);

        final var useCase = new CreateEventTypeUseCase(repo);
        final var ex = assertThrows(IllegalArgumentException.class,
                () -> useCase.execute("conference", "Conferencia", "desc", Set.of(EventCapability.SURVEY)));
        assertEquals("key_already_exists", ex.getMessage());
        Mockito.verify(repo, Mockito.never()).save(Mockito.any());
    }

    @Test
    void create_validKey_savesEventType() {
        final EventTypeRepository repo = Mockito.mock(EventTypeRepository.class);
        Mockito.when(repo.existsByKey("standup")).thenReturn(false);

        final EventType created = new CreateEventTypeUseCase(repo)
                .execute("standup", "Standup", "desc", Set.of(EventCapability.TICKETING_GENERAL, EventCapability.CHAT_BOT));

        assertEquals("standup", created.getKey());
        assertTrue(created.isActive());
        Mockito.verify(repo).save(created);
    }

    @Test
    void update_replacesCapabilitiesCompletely() {
        final EventTypeRepository repo = Mockito.mock(EventTypeRepository.class);
        final EventType existing = new EventType("standup", "Standup", "desc", Set.of(EventCapability.CHAT_BOT));
        Mockito.when(repo.findByUuid(existing.getUuid())).thenReturn(Optional.of(existing));

        final EventType updated = new UpdateEventTypeUseCase(repo)
                .execute(existing.getUuid(), "Standup diario", "nueva desc", Set.of(EventCapability.TICKETING_GENERAL));

        assertEquals(Set.of(EventCapability.TICKETING_GENERAL), updated.getCapabilities());
        assertEquals("Standup diario", updated.getName());
        Mockito.verify(repo).save(existing);
    }

    @Test
    void setActive_deactivate_doesNotDelete() {
        final EventTypeRepository repo = Mockito.mock(EventTypeRepository.class);
        final EventType existing = new EventType("workshop", "Taller", "desc", Set.of(EventCapability.SURVEY));
        Mockito.when(repo.findByUuid(existing.getUuid())).thenReturn(Optional.of(existing));

        final EventType result = new SetEventTypeActiveUseCase(repo).execute(existing.getUuid(), false);

        assertFalse(result.isActive());
        Mockito.verify(repo).save(existing);
    }

    @Test
    void list_activeOnly_filtersInactive() {
        final EventTypeRepository repo = Mockito.mock(EventTypeRepository.class);
        final EventType active = new EventType("conference", "Conferencia", "desc", Set.of());
        Mockito.when(repo.findActive()).thenReturn(List.of(active));

        final List<EventType> result = new ListEventTypesUseCase(repo).execute(true);

        assertEquals(1, result.size());
        Mockito.verify(repo, Mockito.never()).findAll();
    }
}
