package dev.rafex.insightbloom.users.domain.ports;

import dev.rafex.insightbloom.users.domain.model.OrganizerInvitation;
import dev.rafex.insightbloom.users.domain.model.OrganizerInviteSettings;
import dev.rafex.insightbloom.users.domain.model.OrganizerReferral;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface OrganizerInvitationRepository {
    OrganizerInviteSettings settings();
    OrganizerInviteSettings updateSettings(OrganizerInviteSettings settings, String actorUuid, Instant now);
    List<OrganizerInvitation> listInvitations(String status, int limit, int offset);
    Optional<OrganizerInvitation> findInvitation(String uuid);
    OrganizerInvitation createInvitation(String email, String tokenHash, String actorUuid, String referralUuid,
                                         Instant now, Instant expiresAt, String ipHash);
    OrganizerInvitation resendInvitation(String uuid, String tokenHash, String actorUuid,
                                         Instant now, Instant expiresAt, String ipHash);
    void markDelivery(String uuid, boolean delivered, String safeError, Instant now);
    void revokeInvitation(String uuid, String actorUuid, Instant now);
    Optional<OrganizerInvitation> preview(String tokenHash, Instant now);
    void recordPublicAttempt(String eventType, String ipHash, Instant now);
    boolean redeem(String tokenHash, String userUuid, String normalizedEmail, Instant now);
    List<OrganizerReferral> listReferrals(String status, String inviterUuid, int limit, int offset);
    OrganizerReferral submitReferral(String inviterUuid, String email, String ipHash,
                                     int monthlyLimit, Instant now);
    OrganizerInvitation approveReferral(String referralUuid, String tokenHash, String reviewerUuid,
                                        Instant now, Instant expiresAt, String ipHash);
    void rejectReferral(String referralUuid, String reviewerUuid, Instant now);
    void setReferrerEnabled(String userUuid, boolean enabled, String actorUuid, Instant now);
    List<String> listEnabledReferrers();
    List<String> listReferralEligibleOrganizers();
    boolean isReferrerEnabled(String userUuid);
    int remainingReferrals(String userUuid, int monthlyLimit, Instant now);
}
