package dev.rafex.insightbloom.stats.domain.services;
import dev.rafex.insightbloom.stats.domain.model.MessageType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class RelevanceServiceTest {
    private final RelevanceService svc = new RelevanceService();
    @Test
    void calculate_doubt() {
        // 10 visible, doubt (1.2), duda intent (1.25) -> 10 * 1.2 * 1.25 = 15.0
        double score = svc.calculate(10, MessageType.DOUBT, 1.25);
        assertEquals(15.0, score, 0.001);
    }
    @Test
    void calculate_topic() {
        // 5 visible, topic (1.0), pregunta (1.15) -> 5 * 1.0 * 1.15 = 5.75
        double score = svc.calculate(5, MessageType.TOPIC, 1.15);
        assertEquals(5.75, score, 0.001);
    }
    @Test
    void intentWeight_knownValues() {
        assertEquals(1.25, svc.intentWeight("DUDA"), 0.001);
        assertEquals(1.15, svc.intentWeight("PREGUNTA"), 0.001);
        assertEquals(1.00, svc.intentWeight("IDEA"), 0.001);
        assertEquals(1.10, svc.intentWeight("INTERES"), 0.001);
    }
    @Test
    void intentWeight_unknown_returnsDefault() {
        assertEquals(1.0, svc.intentWeight("UNKNOWN"), 0.001);
    }
}
