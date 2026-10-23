package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.Ticket;
import dev.rafex.insightbloom.users.domain.model.TicketDeviceCredential;
import dev.rafex.insightbloom.users.domain.model.TicketDeviceEnrollment;
import dev.rafex.insightbloom.users.domain.ports.EmailPort;
import dev.rafex.insightbloom.users.domain.ports.TicketDeviceRepository;
import dev.rafex.insightbloom.users.domain.ports.TicketRepository;
import dev.rafex.insightbloom.users.domain.services.TicketDeviceCrypto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class TicketDeviceUseCase {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Duration LINK_TTL = Duration.ofDays(7);
    private static final Duration OTP_TTL = Duration.ofMinutes(10);
    private static final Duration SEND_WINDOW = Duration.ofHours(1);
    private static final int MAX_SENDS = 3;
    private final TicketDeviceRepository devices;
    private final TicketRepository tickets;
    private final EmailPort emailPort;
    private final String frontendBaseUrl;
    private OrganizerTicketSignatureUseCase organizerTicketSignatures;

    public TicketDeviceUseCase(final TicketDeviceRepository devices, final TicketRepository tickets,
                               final EmailPort emailPort, final String frontendBaseUrl) {
        this.devices = devices; this.tickets = tickets; this.emailPort = emailPort; this.frontendBaseUrl = frontendBaseUrl;
    }

    public void configureOrganizerTicketSignatures(final OrganizerTicketSignatureUseCase useCase) {
        this.organizerTicketSignatures = useCase;
    }

    public record EnrollmentLink(String url, Instant expiresAt) {}
    public record OtpChallenge(String challengeId, Instant expiresAt) {}
    public record Presentation(String ticketUuid, String credentialUuid, long expiresAt, String signature) {}
    public record OfflineDevice(String ticketUuid, String credentialUuid, String publicKeyJwk, int maxDevices) {}

    public EnrollmentLink createLink(final String conferenceUuid, final String ticketUuid, final String actorUuid) {
        final Ticket ticket = ownedTicket(conferenceUuid, ticketUuid);
        final boolean authenticatedTicketOwner = actorUuid != null && actorUuid.equals(ticket.getClaimedByUserUuid());
        if (!ticket.isDeviceProtected() || (!"ONE_TIME_LINK".equals(ticket.getDeviceEnrollmentMethod()) && !authenticatedTicketOwner))
            throw new IllegalStateException("ticket_link_enrollment_not_enabled");
        final Instant now = Instant.now(); final Instant expires = now.plus(LINK_TTL);
        if (devices.countEnrollmentsSince(ticketUuid, now.minus(SEND_WINDOW)) >= MAX_SENDS)
            throw new IllegalStateException("ticket_enrollment_rate_limited");
        final String token = newToken();
        final var enrollment = new TicketDeviceEnrollment(UUID.randomUUID().toString(), ticketUuid, hash(token),
                "ONE_TIME_LINK", null, expires, null, 0, now);
        devices.createEnrollment(enrollment);
        devices.recordAudit(ticketUuid, null, "ENROLLMENT_LINK_CREATED", actorUuid, "one_time_link", now);
        final String url = frontendBaseUrl.replaceAll("/$", "") + "/ticket-enroll?event="
                + encode(conferenceUuid) + "&ticket=" + encode(ticketUuid) + "#token=" + token;
        if (emailPort.isEnabled() && ticket.getRecipientEmail() != null && !ticket.getRecipientEmail().isBlank()) {
            try {
                emailPort.sendHtml(ticket.getRecipientEmail(), "Activa tu boleto",
                        "<p>Usa este enlace para vincular tu boleto a este dispositivo.</p><p><a href=\""
                                + url.replace("&", "&amp;") + "\">Activar boleto</a></p><p>Es de un solo uso. Si otra persona obtiene el enlace, podría activarlo primero.</p>");
            } catch (final RuntimeException ignored) { /* La interfaz devuelve el enlace para permitir entrega manual. */ }
        }
        return new EnrollmentLink(url, expires);
    }

    public OtpChallenge startOtp(final String conferenceUuid, final String ticketCode) {
        final Ticket ticket = tickets.findByTicketCode(normalizeCode(ticketCode))
                .filter(t -> conferenceUuid.equals(t.getConferenceUuid()))
                .orElseThrow(() -> new IllegalArgumentException("ticket_enrollment_invalid_or_expired"));
        if (!ticket.isDeviceProtected() || ticket.getRecipientEmail() == null || ticket.getRecipientEmail().isBlank())
            throw new IllegalArgumentException("ticket_enrollment_invalid_or_expired");
        final Instant now = Instant.now();
        if (!emailPort.isEnabled() || devices.countEnrollmentsSince(ticket.getUuid(), now.minus(SEND_WINDOW)) >= MAX_SENDS)
            throw new IllegalStateException("ticket_enrollment_unavailable");
        final String otp = String.format(Locale.ROOT, "%06d", RANDOM.nextInt(1_000_000));
        final String challengeId = newToken();
        final Instant expiresAt = now.plus(OTP_TTL);
        final TicketDeviceEnrollment enrollment = new TicketDeviceEnrollment(UUID.randomUUID().toString(), ticket.getUuid(),
                hash(challengeId), "EMAIL_OTP", hashOtp(otp, ticket.getUuid()), expiresAt, null, 0, now);
        devices.createEnrollment(enrollment);
        try {
            emailPort.sendHtml(ticket.getRecipientEmail(), "Código para activar tu boleto",
                    "<p>Tu código para activar el boleto es:</p><p style=\"font-size:28px;font-weight:bold;letter-spacing:6px\">"
                            + otp + "</p><p>Vence en 10 minutos. Si no solicitaste este código, ignora este mensaje.</p>");
        } catch (final RuntimeException e) {
            devices.recordEnrollmentFailure(enrollment.uuid());
            throw new IllegalStateException("ticket_enrollment_unavailable");
        }
        return new OtpChallenge(challengeId, expiresAt);
    }

    public TicketDeviceCredential complete(final String conferenceUuid, final String enrollmentToken,
            final String otp, final String claimantUuid, final String publicKeyJwk,
            final String label, final String fingerprint, final boolean replaceOldest) {
        if (enrollmentToken == null || enrollmentToken.isBlank() || claimantUuid == null || claimantUuid.isBlank())
            throw new IllegalArgumentException("ticket_enrollment_invalid_or_expired");
        if (!TicketDeviceCrypto.validPublicJwk(publicKeyJwk)) throw new IllegalArgumentException("ticket_device_public_key_invalid");
        final var enrollment = devices.findEnrollmentByHash(hash(enrollmentToken))
                .filter(e -> e.consumedAt() == null && e.expiresAt().isAfter(Instant.now()) && e.failedAttempts() < 5)
                .orElseThrow(() -> new IllegalArgumentException("ticket_enrollment_invalid_or_expired"));
        final Ticket ticket = tickets.findByUuid(enrollment.ticketUuid())
                .filter(t -> conferenceUuid.equals(t.getConferenceUuid()))
                .orElseThrow(() -> new IllegalArgumentException("ticket_enrollment_invalid_or_expired"));
        if ("EMAIL_OTP".equals(enrollment.method())
                && (otp == null || !MessageDigest.isEqual(hashOtp(otp, enrollment.ticketUuid()).getBytes(StandardCharsets.US_ASCII), enrollment.otpHash().getBytes(StandardCharsets.US_ASCII)))) {
            devices.recordEnrollmentFailure(enrollment.uuid());
            throw new IllegalArgumentException("ticket_otp_invalid");
        }
        final Instant now = Instant.now();
        final String credentialUuid = UUID.randomUUID().toString();
        final String safeLabel = label == null || label.isBlank() ? "Dispositivo" : label.trim().substring(0, Math.min(80, label.trim().length()));
        final TicketDeviceCredential enrolled = devices.enrollAndClaim(enrollment.uuid(), claimantUuid, credentialUuid, ticket.getUuid(), publicKeyJwk,
                safeLabel, fingerprint == null ? null : fingerprint.substring(0, Math.min(256, fingerprint.length())),
                ticket.getDeviceMaxDevices(), replaceOldest, now).orElseThrow(() -> new IllegalArgumentException("ticket_enrollment_invalid_or_expired"));
        if (organizerTicketSignatures != null) organizerTicketSignatures.invalidateOfflinePackage(conferenceUuid);
        return enrolled;
    }

    public List<TicketDeviceCredential> list(final String conferenceUuid, final String ticketUuid, final String actorUuid) {
        ownedTicket(conferenceUuid, ticketUuid);
        return devices.listByTicket(ticketUuid);
    }

    public void revoke(final String conferenceUuid, final String ticketUuid, final String credentialUuid, final String actorUuid) {
        ownedTicket(conferenceUuid, ticketUuid);
        final Instant now = Instant.now();
        if (!devices.revokeCredential(ticketUuid, credentialUuid, now)) throw new IllegalArgumentException("ticket_device_not_found");
        if (organizerTicketSignatures != null) organizerTicketSignatures.invalidateOfflinePackage(conferenceUuid);
        devices.recordAudit(ticketUuid, credentialUuid, "DEVICE_REVOKED", actorUuid, "owner_action", now);
    }

    public boolean verifyPresentation(final String conferenceUuid, final String ticketUuid, final String credentialUuid,
                                     final long expiresAt, final String signature) {
        final Ticket ticket = tickets.findByUuid(ticketUuid).filter(t -> conferenceUuid.equals(t.getConferenceUuid()))
                .orElseThrow(() -> new IllegalArgumentException("ticket_not_found"));
        if (!ticket.isDeviceProtected()) return false;
        final long now = Instant.now().getEpochSecond();
        if (expiresAt < now - 10 || expiresAt > now + 90) return false;
        final TicketDeviceCredential credential = devices.findActiveCredential(credentialUuid, ticketUuid)
                .orElseThrow(() -> new IllegalArgumentException("ticket_device_not_authorized"));
        final String payload = "insightbloom-ticket-v1|" + ticketUuid + "|" + credentialUuid + "|" + expiresAt;
        return TicketDeviceCrypto.verify(credential.publicKeyJwk(), payload, signature);
    }

    public void recordAudit(final String conferenceUuid, final String ticketUuid, final String credentialUuid,
                            final String action, final String actorUuid, final String detail) {
        ownedTicket(conferenceUuid, ticketUuid);
        devices.recordAudit(ticketUuid, credentialUuid, action, actorUuid,
                detail == null ? "" : detail.substring(0, Math.min(120, detail.length())), Instant.now());
    }

    public List<OfflineDevice> offlineDevices(final String conferenceUuid) {
        return devices.listActiveForConference(conferenceUuid).stream().map(d -> {
            final Ticket t = tickets.findByUuid(d.ticketUuid()).orElseThrow();
            return new OfflineDevice(d.ticketUuid(), d.uuid(), d.publicKeyJwk(), t.getDeviceMaxDevices());
        }).toList();
    }

    public boolean verifyOfflineBundle(final String payload, final String signature) {
        return organizerTicketSignatures != null && organizerTicketSignatures.isStoredOfflinePackage(
                conferenceUuidFromOfflinePayload(payload), payload, signature);
    }

    private static String conferenceUuidFromOfflinePayload(final String payload) {
        if (payload == null || payload.length() > 5_000_000) return "";
        final var matcher = java.util.regex.Pattern.compile("\\\"conferenceUuid\\\":\\\"([0-9a-fA-F-]{36})\\\"").matcher(payload);
        return matcher.find() ? matcher.group(1) : "";
    }

    public boolean verifyOfflinePresentation(final String conferenceUuid, final String ticketUuid, final String credentialUuid,
            final long expiresAt, final String signature, final long scannedAt, final long bundleIssuedAt,
            final long bundleExpiresAt, final String bundledPublicKeyJwk) {
        final long now = Instant.now().getEpochSecond();
        if (scannedAt > now + 300 || scannedAt < bundleIssuedAt || scannedAt > bundleExpiresAt
                || bundleExpiresAt - bundleIssuedAt > Duration.ofHours(24).getSeconds()
                || expiresAt < scannedAt - 10 || expiresAt > scannedAt + 90) return false;
        final Ticket ticket = tickets.findByUuid(ticketUuid).filter(t -> conferenceUuid.equals(t.getConferenceUuid()))
                .orElseThrow(() -> new IllegalArgumentException("ticket_not_found"));
        if (!ticket.isDeviceProtected()) return false;
        if (bundledPublicKeyJwk == null || !TicketDeviceCrypto.validPublicJwk(bundledPublicKeyJwk)) return false;
        final String payload = "insightbloom-ticket-v1|" + ticketUuid + "|" + credentialUuid + "|" + expiresAt;
        return TicketDeviceCrypto.verify(bundledPublicKeyJwk, payload, signature);
    }

    private Ticket ownedTicket(final String conferenceUuid, final String ticketUuid) {
        return tickets.findByUuid(ticketUuid).filter(t -> conferenceUuid.equals(t.getConferenceUuid()))
                .orElseThrow(() -> new IllegalArgumentException("ticket_not_found"));
    }

    private static String newToken() { final byte[] bytes = new byte[32]; RANDOM.nextBytes(bytes); return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    private static String hash(final String value) {
        try { return Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (final Exception e) { throw new IllegalStateException(e); }
    }
    private static String hashOtp(final String otp, final String ticketUuid) {
        final PBEKeySpec spec = new PBEKeySpec(otp.toCharArray(), ticketUuid.getBytes(StandardCharsets.UTF_8), 120_000, 256);
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded());
        } catch (final Exception e) { throw new IllegalStateException("ticket_enrollment_unavailable", e); }
        finally { spec.clearPassword(); }
    }
    private static String normalizeCode(final String value) { return TicketUseCase.normalizeCode(value); }
    private static String encode(final String value) { return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8); }
}
