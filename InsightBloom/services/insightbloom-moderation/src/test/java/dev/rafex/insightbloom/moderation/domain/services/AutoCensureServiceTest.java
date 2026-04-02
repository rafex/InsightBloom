package dev.rafex.insightbloom.moderation.domain.services;
import dev.rafex.insightbloom.moderation.domain.model.BlockedTerm;
import dev.rafex.insightbloom.moderation.domain.ports.BlockedTermRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class AutoCensureServiceTest {
    @Test
    void isWordBlocked_matchesBlockedTerm() {
        BlockedTermRepository repo = Mockito.mock(BlockedTermRepository.class);
        var term = new BlockedTerm("all", "spam", "system");
        Mockito.when(repo.findAllActive()).thenReturn(List.of(term));
        AutoCensureService svc = new AutoCensureService(repo);
        assertTrue(svc.isWordBlocked("spam"));
    }
    @Test
    void isWordBlocked_noMatch() {
        BlockedTermRepository repo = Mockito.mock(BlockedTermRepository.class);
        Mockito.when(repo.findAllActive()).thenReturn(List.of());
        AutoCensureService svc = new AutoCensureService(repo);
        assertFalse(svc.isWordBlocked("inteligencia"));
    }
    @Test
    void evaluate_wordAndDetailBlocked() {
        BlockedTermRepository repo = Mockito.mock(BlockedTermRepository.class);
        var wTerm = new BlockedTerm("word","spam","system");
        var dTerm = new BlockedTerm("detail","offtopic","system");
        Mockito.when(repo.findAllActive()).thenReturn(List.of(wTerm, dTerm));
        AutoCensureService svc = new AutoCensureService(repo);
        var result = svc.evaluate("spam", "esto es offtopic");
        assertTrue(result.wordBlocked());
        assertTrue(result.detailBlocked());
    }
}
