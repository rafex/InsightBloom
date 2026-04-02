package dev.rafex.insightbloom.ingest.domain.ports;
public interface ModerationPort {
    record EvaluationResult(boolean wordBlocked, boolean detailBlocked) {}
    EvaluationResult evaluate(String word, String detail);
}
