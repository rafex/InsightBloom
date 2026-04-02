package dev.rafex.insightbloom.stats.domain.services;
import dev.rafex.insightbloom.stats.domain.model.MessageType;
import dev.rafex.insightbloom.stats.domain.model.WordIntent;
public class RelevanceService {
    public double calculate(long visibleCount, MessageType type, double avgIntentWeight) {
        double typeWeight = type == MessageType.DOUBT ? 1.2 : 1.0;
        return visibleCount * typeWeight * avgIntentWeight;
    }
    public double intentWeight(String intent) {
        if (intent == null) return 1.0;
        try { return WordIntent.valueOf(intent.toUpperCase()).weight(); }
        catch (IllegalArgumentException e) { return 1.0; }
    }
}
