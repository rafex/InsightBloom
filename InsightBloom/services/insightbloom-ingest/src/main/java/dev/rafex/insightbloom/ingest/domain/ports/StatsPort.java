package dev.rafex.insightbloom.ingest.domain.ports;
public interface StatsPort {
    record RecalcRequest(String conferenceUuid, String wordNormalized, String wordCanonical,
                         String messageType, String wordIntent, boolean visible) {}
    void recalc(RecalcRequest request);
}
