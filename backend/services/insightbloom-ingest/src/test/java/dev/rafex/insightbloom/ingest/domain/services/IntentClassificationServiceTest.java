package dev.rafex.insightbloom.ingest.domain.services;
import dev.rafex.insightbloom.ingest.domain.model.DetailIntent;
import dev.rafex.insightbloom.ingest.domain.model.WordIntent;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class IntentClassificationServiceTest {
    private final IntentClassificationService svc = new IntentClassificationService();
    @Test void classifyWord_question() {
        assertEquals(WordIntent.PREGUNTA, svc.classifyWord("ia", "como funciona?"));
    }
    @Test void classifyWord_default() {
        assertEquals(WordIntent.INTERES, svc.classifyWord("blockchain", "topic interesante"));
    }
    @Test void classifyDetail_pregunta() {
        assertEquals(DetailIntent.PREGUNTA, svc.classifyDetail("Como funciona esto?"));
    }
    @Test void classifyDetail_propuesta() {
        assertEquals(DetailIntent.PROPUESTA, svc.classifyDetail("Podria usarse para mejorar"));
    }
}
