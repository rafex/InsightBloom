package dev.rafex.insightbloom.ingest.domain.services;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class WordNormalizationServiceTest {
    private final WordNormalizationService svc = new WordNormalizationService();
    @Test void normalize_lowercaseAndStripAccents() {
        assertEquals("ia", svc.normalize("IA"));
        assertEquals("servicio", svc.normalize("Servicio"));
        assertEquals("inteligencia artificial", svc.normalize("Inteligencia Artificial"));
    }
    @Test void normalize_accentedWord() {
        assertEquals("logica", svc.normalize("Lógica"));
    }
    @Test void canonical_pluralToSingular() {
        assertEquals("servicio", svc.canonical("servicios", "servicio"));
    }
    @Test void canonical_alreadySingular() {
        assertEquals("servicio", svc.canonical("servicio", "servicio"));
    }
    @Test void canonical_newWord() {
        assertEquals("ia", svc.canonical("ia", null));
    }
}
