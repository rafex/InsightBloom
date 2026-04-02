package dev.rafex.insightbloom.ingest.domain.ports;
public interface QueryPort {
    record UpdateRequest(String conferenceUuid, String wordNormalized, String wordCanonical,
                         String messageType, double relevanceScore, long messageCount,
                         String messageUuid, String authorLabel, String authorKind,
                         String detailVisible, String receivedAt, boolean wordVisible) {}
    void update(UpdateRequest request);
}
