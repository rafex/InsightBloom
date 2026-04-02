package dev.rafex.insightbloom.users.domain.services;

import dev.rafex.insightbloom.users.domain.ports.ConferenceRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;

class FriendlyIdServiceTest {
    @Test
    void toSlug_convertsSpacesAndAccents() {
        assertEquals("conferencia-ia-2026", FriendlyIdService.toSlug("Conferencia IA 2026"));
    }

    @Test
    void toSlug_stripsSpecialChars() {
        assertEquals("hello-world", FriendlyIdService.toSlug("Hello, World!"));
    }

    @Test
    void toSlug_emptyString() {
        assertEquals("", FriendlyIdService.toSlug("   "));
    }

    @Test
    void generate_noCollision() {
        ConferenceRepository repo = Mockito.mock(ConferenceRepository.class);
        Mockito.when(repo.existsByFriendlyId(Mockito.anyString())).thenReturn(false);
        FriendlyIdService svc = new FriendlyIdService(repo);
        String id = svc.generate("Conferencia IA 2026");
        assertEquals("conferencia-ia-2026", id);
    }

    @Test
    void generate_withCollision() {
        ConferenceRepository repo = Mockito.mock(ConferenceRepository.class);
        Mockito.when(repo.existsByFriendlyId("conferencia-ia-2026")).thenReturn(true);
        Mockito.when(repo.existsByFriendlyId("conferencia-ia-2026-2")).thenReturn(false);
        FriendlyIdService svc = new FriendlyIdService(repo);
        assertEquals("conferencia-ia-2026-2", svc.generate("Conferencia IA 2026"));
    }

    @Test
    void generate_emptyName_usesConf() {
        ConferenceRepository repo = Mockito.mock(ConferenceRepository.class);
        Mockito.when(repo.existsByFriendlyId(Mockito.anyString())).thenReturn(false);
        FriendlyIdService svc = new FriendlyIdService(repo);
        String id = svc.generate("   ");
        assertEquals("conf", id);
    }
}
