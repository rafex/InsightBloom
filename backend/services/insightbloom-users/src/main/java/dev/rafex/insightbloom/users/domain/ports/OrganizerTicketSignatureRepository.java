package dev.rafex.insightbloom.users.domain.ports;

import java.time.Instant;
import java.util.Optional;
import java.util.List;

public interface OrganizerTicketSignatureRepository {
    record EventKey(String conferenceUuid, String keyId, String publicKeySpki, String state, Instant createdAt) {}
    record OfflinePackage(String conferenceUuid, String keyId, String payload, String coseSign1, Instant issuedAt, Instant expiresAt) {}
    Optional<EventKey> activeKey(String conferenceUuid);
    Optional<EventKey> key(String conferenceUuid, String keyId);
    List<EventKey> keys(String conferenceUuid);
    void registerKey(String conferenceUuid, String keyId, String publicKeySpki, String actorUuid, Instant now);
    boolean revokeActiveKey(String conferenceUuid, String actorUuid, Instant now);
    boolean saveTicketSignature(String conferenceUuid, String ticketUuid, String keyId, String coseSign1, String actorUuid, Instant now);
    Optional<String> ticketSignature(String conferenceUuid, String ticketUuid);
    boolean hasTicketSignature(String conferenceUuid, String ticketUuid);
    void saveOfflinePackage(OfflinePackage offlinePackage, String actorUuid);
    Optional<OfflinePackage> offlinePackage(String conferenceUuid);
    boolean isKnownOfflinePackage(String conferenceUuid, String payload, String coseSign1, Instant now);
    void saveOfflineDraft(String conferenceUuid, String payload, Instant expiresAt);
    Optional<String> offlineDraft(String conferenceUuid, Instant now);
    void clearOfflineDraft(String conferenceUuid);
    void invalidateOfflinePackage(String conferenceUuid);
}
