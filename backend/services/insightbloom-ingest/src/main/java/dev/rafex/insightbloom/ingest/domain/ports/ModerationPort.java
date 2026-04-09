package dev.rafex.insightbloom.ingest.domain.ports;
public interface ModerationPort {
    record EvaluationResult(boolean wordBlocked, boolean detailBlocked) {}
    EvaluationResult evaluate(String word, String detail, String conferenceUuid, String wordCanonical, String messageUuid, String wordText, String detailText);
}
