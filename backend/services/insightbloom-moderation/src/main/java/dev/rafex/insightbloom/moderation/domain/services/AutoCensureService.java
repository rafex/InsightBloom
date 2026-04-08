package dev.rafex.insightbloom.moderation.domain.services;
import dev.rafex.insightbloom.moderation.domain.ports.BlockedTermRepository;
import java.text.Normalizer;
public class AutoCensureService {
    private final BlockedTermRepository blockedTermRepository;
    public AutoCensureService(BlockedTermRepository blockedTermRepository) {
        this.blockedTermRepository = blockedTermRepository;
    }
    public boolean isWordBlocked(String word) {
        if (word == null || word.isBlank()) return false;
        String normalized = normalize(word);
        return blockedTermRepository.findAllActive().stream()
            .filter(t -> "word".equals(t.getScope()) || "all".equals(t.getScope()))
            .anyMatch(t -> normalized.contains(t.getTermNormalized()));
    }
    public boolean isDetailBlocked(String detail) {
        if (detail == null || detail.isBlank()) return false;
        String normalized = normalize(detail);
        return blockedTermRepository.findAllActive().stream()
            .filter(t -> "detail".equals(t.getScope()) || "all".equals(t.getScope()))
            .anyMatch(t -> normalized.contains(t.getTermNormalized()));
    }
    public record EvaluationResult(boolean wordBlocked, boolean detailBlocked) {}
    public EvaluationResult evaluate(String word, String detail) {
        return new EvaluationResult(isWordBlocked(word), isDetailBlocked(detail));
    }
    static String normalize(String input) {
        return Normalizer.normalize(input, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "").toLowerCase().trim();
    }
}
