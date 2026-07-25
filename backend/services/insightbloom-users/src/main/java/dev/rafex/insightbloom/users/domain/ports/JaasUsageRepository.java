package dev.rafex.insightbloom.users.domain.ports;

/** Persistent local estimate of the JaaS monthly participant usage. */
public interface JaasUsageRepository {
    void recordUniqueParticipant(String month, String userUuid);

    int countUniqueParticipants(String month);
}
