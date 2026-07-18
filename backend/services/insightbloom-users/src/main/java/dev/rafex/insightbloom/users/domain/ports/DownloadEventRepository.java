package dev.rafex.insightbloom.users.domain.ports;

public interface DownloadEventRepository {
    /** kind: "certificate" o "presentation". userUuid nullable (descargas no atribuibles a un usuario). */
    void record(String conferenceUuid, String kind, String userUuid);

    long countByConferenceAndKind(String conferenceUuid, String kind);

    boolean existsByConferenceAndUserAndKind(String conferenceUuid, String userUuid, String kind);
}
