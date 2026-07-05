package dev.rafex.insightbloom.users.domain.ports;

public interface DownloadEventRepository {
    /** kind: "certificate" o "presentation". */
    void record(String conferenceUuid, String kind);

    long countByConferenceAndKind(String conferenceUuid, String kind);
}
