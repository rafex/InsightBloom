package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.AuthMethod;
import dev.rafex.insightbloom.users.domain.model.OtpChannel;
import dev.rafex.insightbloom.users.domain.model.OtpCode;
import dev.rafex.insightbloom.users.domain.model.OtpRequestAudit;
import dev.rafex.insightbloom.users.domain.model.User;
import dev.rafex.insightbloom.users.domain.model.UserStatus;
import dev.rafex.insightbloom.users.domain.ports.EmailPort;
import dev.rafex.insightbloom.users.domain.ports.OtpCodeRepository;
import dev.rafex.insightbloom.users.domain.ports.OtpRequestAuditPort;
import dev.rafex.insightbloom.users.domain.ports.UserRepository;
import dev.rafex.insightbloom.users.domain.services.OtpEmailTemplate;

import java.time.Duration;
import java.time.Instant;
import java.security.SecureRandom;
import java.util.Optional;

/** Requests email OTP without exposing account existence or account state to the caller. */
public class RequestLoginOtpUseCase {
    private static final int MAX_SENDS_PER_WINDOW = 3;
    private static final Duration RATE_LIMIT_WINDOW = Duration.ofHours(1);
    private static final Duration CODE_TTL = Duration.ofMinutes(10);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final OtpCodeRepository otpCodeRepository;
    private final OtpRequestAuditPort auditPort;
    private final EmailPort emailPort;

    public RequestLoginOtpUseCase(final UserRepository userRepository, final OtpCodeRepository otpCodeRepository,
                                  final OtpRequestAuditPort auditPort, final EmailPort emailPort) {
        this.userRepository = userRepository;
        this.otpCodeRepository = otpCodeRepository;
        this.auditPort = auditPort;
        this.emailPort = emailPort;
    }

    public record Request(String identifier, String clientIp, String userAgent) {
        public Request(final String identifier) { this(identifier, null, null); }
    }

    /** Invalid/blank input remains a 400; all well-formed requests complete generically. */
    public void execute(final Request request) {
        if (request == null || request.identifier() == null || request.identifier().isBlank()) {
            throw new IllegalArgumentException("identifier_required");
        }

        final Instant requestedAt = Instant.now();
        String accountUuid = null;
        String outcome = "request_failed";
        try {
            final Optional<User> found = findByIdentifier(request.identifier());
            if (found.isEmpty()) {
                outcome = "account_not_found";
                return;
            }
            final User user = found.get();
            accountUuid = user.getUuid();
            if (user.getStatus() != UserStatus.ACTIVE) {
                outcome = "account_inactive";
                return;
            }
            if (user.getAuthMethod() != AuthMethod.OTP_EMAIL) {
                outcome = "otp_not_enabled";
                return;
            }
            final String email = user.getEmail();
            if (email == null || email.isBlank()) {
                outcome = "email_missing";
                return;
            }
            if (!emailPort.isEnabled()) {
                outcome = "mail_provider_disabled";
                return;
            }
            // Keep rate limiting independent from audit writes so an audit outage cannot permit
            // repeated SMTP sends. Pending/failed OTP records are excluded by countSince.
            if (otpCodeRepository.countSince(email, requestedAt.minus(RATE_LIMIT_WINDOW)) >= MAX_SENDS_PER_WINDOW) {
                outcome = "rate_limited";
                return;
            }

            final String code = String.format("%06d", RANDOM.nextInt(1_000_000));
            final OtpCode otpCode = new OtpCode(email, OtpChannel.EMAIL, code, requestedAt.plus(CODE_TTL));
            // Pending codes cannot be verified or counted against the send limit until SMTP accepts them.
            otpCodeRepository.savePending(otpCode);
            try {
                emailPort.sendHtml(email, "Tu código de acceso a InsightBloom", OtpEmailTemplate.render(code));
            } catch (final RuntimeException sendFailure) {
                try {
                    otpCodeRepository.markConsumed(otpCode.getUuid());
                } catch (final RuntimeException ignored) {
                    // delivered=0 still prevents verification and rate-limit consumption.
                }
                outcome = "smtp_failed";
                return;
            }
            otpCodeRepository.markDelivered(otpCode.getUuid());
            outcome = "smtp_accepted";
        } catch (final RuntimeException ignored) {
            // Keep the public response indistinguishable and do not expose provider/database details.
            outcome = "request_failed";
        } finally {
            try {
                auditPort.save(new OtpRequestAudit(requestedAt, accountUuid, request.clientIp(),
                        request.userAgent(), outcome));
            } catch (final RuntimeException auditFailure) {
                System.err.println("{\"event\":\"otp_audit_write_failed\",\"errorType\":\""
                        + auditFailure.getClass().getSimpleName() + "\"}");
            }
        }
    }

    private Optional<User> findByIdentifier(final String identifier) {
        return userRepository.findByUsername(identifier).or(() -> userRepository.findByEmail(identifier));
    }
}
