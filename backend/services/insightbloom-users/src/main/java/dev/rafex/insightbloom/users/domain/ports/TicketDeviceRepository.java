package dev.rafex.insightbloom.users.domain.ports;

import dev.rafex.insightbloom.users.domain.model.TicketDeviceCredential;
import dev.rafex.insightbloom.users.domain.model.TicketDeviceEnrollment;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TicketDeviceRepository {
    void createEnrollment(TicketDeviceEnrollment enrollment);
    Optional<TicketDeviceEnrollment> findEnrollmentByHash(String tokenHash);
    int countEnrollmentsSince(String ticketUuid, Instant since);
    boolean recordEnrollmentFailure(String enrollmentUuid);
    Optional<TicketDeviceCredential> enrollAndClaim(String enrollmentUuid, String claimantUuid,
            String credentialUuid, String ticketUuid, String publicKeyJwk, String label,
            String fingerprint, int maxDevices, boolean replaceOldest, Instant now);
    List<TicketDeviceCredential> listByTicket(String ticketUuid);
    Optional<TicketDeviceCredential> findActiveCredential(String credentialUuid, String ticketUuid);
    boolean revokeCredential(String ticketUuid, String credentialUuid, Instant revokedAt);
    void recordAudit(String ticketUuid, String credentialUuid, String action, String actorUuid,
            String detail, Instant occurredAt);
    List<TicketDeviceCredential> listActiveForConference(String conferenceUuid);
}
