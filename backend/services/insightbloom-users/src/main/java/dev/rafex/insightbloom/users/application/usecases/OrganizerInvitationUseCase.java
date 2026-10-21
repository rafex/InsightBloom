package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.OrganizerInvitation;
import dev.rafex.insightbloom.users.domain.model.OrganizerInviteSettings;
import dev.rafex.insightbloom.users.domain.model.OrganizerReferral;
import dev.rafex.insightbloom.users.domain.model.User;
import dev.rafex.insightbloom.users.domain.ports.EmailPort;
import dev.rafex.insightbloom.users.domain.ports.OrganizerInvitationRepository;
import dev.rafex.insightbloom.users.domain.ports.UserRepository;
import dev.rafex.insightbloom.users.domain.services.TokenService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

public final class OrganizerInvitationUseCase {
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final SecureRandom RANDOM = new SecureRandom();
    private final OrganizerInvitationRepository repository;
    private final UserRepository users;
    private final EmailPort emailPort;
    private final TokenService tokenService;
    private final String frontendBaseUrl;

    public OrganizerInvitationUseCase(final OrganizerInvitationRepository repository, final UserRepository users,
            final EmailPort emailPort, final TokenService tokenService, final String frontendBaseUrl) {
        this.repository = repository; this.users = users; this.emailPort = emailPort;
        this.tokenService = tokenService; this.frontendBaseUrl = frontendBaseUrl;
    }

    public record InvitePreview(String emailHint, Instant expiresAt) {}
    public record ReferralQueue(List<OrganizerReferral> items, int remaining, int limit, boolean enabled) {}
    public record RedeemResult(String token, String userUuid, String role, String expiresAt) {}

    public OrganizerInviteSettings settings() { return repository.settings(); }

    public OrganizerInviteSettings updateSettings(final boolean referralsEnabled, final int ttlDays,
            final int referralLimit, final String actorUuid) {
        return repository.updateSettings(new OrganizerInviteSettings(referralsEnabled, ttlDays, referralLimit), actorUuid, Instant.now());
    }

    public List<OrganizerInvitation> listInvitations(final String status, final int limit, final int offset) {
        return repository.listInvitations(status, limit, offset);
    }

    public OrganizerInvitation createInvitation(final String rawEmail, final String actorUuid, final String ipHash) {
        final String email = normalizeAndValidate(rawEmail);
        final var settings = repository.settings();
        final String rawToken = newToken();
        final var row = repository.createInvitation(email, hash(rawToken), actorUuid, null, Instant.now(),
                Instant.now().plus(settings.invitationTtlDays(), ChronoUnit.DAYS), ipHash);
        deliver(row, rawToken);
        return repository.findInvitation(row.uuid()).orElse(row);
    }

    public OrganizerInvitation resendInvitation(final String uuid, final String actorUuid, final String ipHash) {
        final String rawToken = newToken(); final Instant now = Instant.now();
        final var settings = repository.settings();
        final var row = repository.resendInvitation(uuid, hash(rawToken), actorUuid, now,
                now.plus(settings.invitationTtlDays(), ChronoUnit.DAYS), ipHash);
        deliver(row, rawToken);
        return repository.findInvitation(row.uuid()).orElse(row);
    }

    private void deliver(final OrganizerInvitation row, final String rawToken) {
        if (!emailPort.isEnabled()) { repository.markDelivery(row.uuid(), false, "email_provider_unavailable", Instant.now()); return; }
        try {
            final String url = frontendBaseUrl.replaceAll("/$", "") + "/organizer-invite#token=" + rawToken;
            emailPort.sendHtml(row.email(), "Invitación para organizar eventos en InsightBloom", invitationHtml(row.email(), url, row.expiresAt()));
            repository.markDelivery(row.uuid(), true, null, Instant.now());
        } catch (final Exception e) {
            // Do not persist provider details, which can contain addresses or transport metadata.
            repository.markDelivery(row.uuid(), false, "email_delivery_failed", Instant.now());
        }
    }

    public void revokeInvitation(final String uuid, final String actorUuid) {
        repository.revokeInvitation(uuid, actorUuid, Instant.now());
    }

    public Optional<InvitePreview> preview(final String rawToken) {
        if (!validToken(rawToken)) return Optional.empty();
        return repository.preview(hash(rawToken), Instant.now()).map(i -> new InvitePreview(maskEmail(i.email()), i.expiresAt()));
    }

    public void recordPublicAttempt(final String eventType, final String ipHash) {
        repository.recordPublicAttempt(eventType, ipHash, Instant.now());
    }

    public RedeemResult redeem(final String rawToken, final String userUuid) {
        if (!validToken(rawToken)) throw new IllegalArgumentException("invitation_invalid_or_expired");
        final User user = users.findByUuid(userUuid).orElseThrow(() -> new IllegalArgumentException("invitation_invalid_or_expired"));
        if (!user.isEmailVerified() || user.getEmail() == null || user.getEmail().isBlank()
                || user.getStatus() != dev.rafex.insightbloom.users.domain.model.UserStatus.ACTIVE) {
            throw new IllegalArgumentException("invitation_invalid_or_expired");
        }
        if (!repository.redeem(hash(rawToken), userUuid, normalizeEmail(user.getEmail()), Instant.now()))
            throw new IllegalArgumentException("invitation_invalid_or_expired");
        final var token = tokenService.issueUserToken(userUuid, dev.rafex.insightbloom.users.domain.model.TokenKind.USER);
        final User updated = users.findByUuid(userUuid).orElseThrow();
        return new RedeemResult(token.getTokenValue(), userUuid,
                dev.rafex.insightbloom.users.domain.model.UserRole.toCsv(updated.getRoles()), token.getExpiresAt().toString());
    }

    public OrganizerReferral submitReferral(final String inviterUuid, final String rawEmail, final String ipHash) {
        final String email = normalizeAndValidate(rawEmail);
        final var settings = repository.settings();
        return repository.submitReferral(inviterUuid, email, ipHash, settings.referralLimit(), Instant.now());
    }

    public ReferralQueue myReferrals(final String inviterUuid) {
        final var settings = repository.settings();
        return new ReferralQueue(repository.listReferrals(null, inviterUuid, 100, 0),
                repository.remainingReferrals(inviterUuid, settings.referralLimit(), Instant.now()),
                settings.referralLimit(), settings.referralsEnabled() && repository.isReferrerEnabled(inviterUuid));
    }

    public List<OrganizerReferral> listReferrals(final String status, final int limit, final int offset) {
        return repository.listReferrals(status, null, limit, offset);
    }

    public OrganizerInvitation approveReferral(final String referralUuid, final String reviewerUuid, final String ipHash) {
        final String rawToken = newToken(); final Instant now = Instant.now(); final var settings = repository.settings();
        final var row = repository.approveReferral(referralUuid, hash(rawToken), reviewerUuid, now,
                now.plus(settings.invitationTtlDays(), ChronoUnit.DAYS), ipHash);
        deliver(row, rawToken);
        return repository.findInvitation(row.uuid()).orElse(row);
    }

    public void rejectReferral(final String referralUuid, final String reviewerUuid) {
        repository.rejectReferral(referralUuid, reviewerUuid, Instant.now());
    }

    public void setReferrerEnabled(final String userUuid, final boolean enabled, final String actorUuid) {
        repository.setReferrerEnabled(userUuid, enabled, actorUuid, Instant.now());
    }

    public List<String> listEnabledReferrers() { return repository.listEnabledReferrers(); }
    public List<String> listReferralEligibleOrganizers() { return repository.listReferralEligibleOrganizers(); }

    private static String normalizeAndValidate(final String raw) {
        final String email = normalizeEmail(raw);
        if (!EMAIL.matcher(email).matches() || email.length() > 254) throw new IllegalArgumentException("invalid_email");
        return email;
    }
    private static String normalizeEmail(final String value) { return value == null ? "" : value.trim().toLowerCase(Locale.ROOT); }
    private static String newToken() { final byte[] bytes = new byte[32]; RANDOM.nextBytes(bytes); return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    private static boolean validToken(final String token) { return token != null && token.matches("[A-Za-z0-9_-]{43}"); }
    private static String hash(final String token) {
        try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
    private static String maskEmail(final String email) {
        final int at=email.indexOf('@'); if(at<1)return "•••";
        final String local=email.substring(0,at); final String domain=email.substring(at+1);
        return local.substring(0,1)+"•••@"+domain;
    }
    private static String escape(final String s) { return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&#39;"); }
    private static String invitationHtml(final String email,final String url,final Instant expiresAt) {
        return "<div style='font-family:Arial,sans-serif;max-width:560px;margin:auto;color:#1f2937'>"
            +"<h1 style='color:#166534'>Invitación a InsightBloom</h1><p>Se te invitó a crear y organizar eventos en InsightBloom.</p>"
            +"<p>La invitación está vinculada a <strong>"+escape(email)+"</strong> y vence el <strong>"+escape(expiresAt.toString())+"</strong>.</p>"
            +"<p><a href='"+escape(url)+"' style='display:inline-block;padding:12px 20px;background:#166534;color:white;text-decoration:none;border-radius:8px'>Revisar invitación</a></p>"
            +"<p>El enlace no activa tu cuenta hasta que confirmes la invitación. Si no esperabas este mensaje, puedes ignorarlo.</p></div>";
    }
}
