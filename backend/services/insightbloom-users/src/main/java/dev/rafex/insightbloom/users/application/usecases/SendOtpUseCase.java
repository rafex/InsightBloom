package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.OtpChannel;
import dev.rafex.insightbloom.users.domain.model.OtpCode;
import dev.rafex.insightbloom.users.domain.ports.EmailPort;
import dev.rafex.insightbloom.users.domain.ports.OtpCodeRepository;
import dev.rafex.insightbloom.users.domain.ports.SmsPort;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

public class SendOtpUseCase {
    private static final SecureRandom RANDOM = new SecureRandom();

    private final OtpCodeRepository otpCodeRepository;
    private final SmsPort smsPort;
    private final EmailPort emailPort;

    public SendOtpUseCase(final OtpCodeRepository otpCodeRepository, final SmsPort smsPort,
                           final EmailPort emailPort) {
        this.otpCodeRepository = otpCodeRepository;
        this.smsPort = smsPort;
        this.emailPort = emailPort;
    }

    public record Request(String identifier, String channel) {}

    public void execute(final Request req) {
        if (req.identifier() == null || req.identifier().isBlank()) {
            throw new IllegalArgumentException("identifier_required");
        }
        final OtpChannel channel = OtpChannel.valueOf((req.channel() == null ? "EMAIL" : req.channel()).toUpperCase());
        if (channel == OtpChannel.SMS && !smsPort.isEnabled()) {
            throw new IllegalStateException("sms_provider_not_configured");
        }
        if (channel == OtpChannel.EMAIL && !emailPort.isEnabled()) {
            throw new IllegalStateException("email_provider_not_configured");
        }

        final String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        final OtpCode otpCode = new OtpCode(req.identifier(), channel, code, Instant.now().plus(10, ChronoUnit.MINUTES));
        otpCodeRepository.save(otpCode);

        final String message = "Tu código de verificación InsightBloom es: " + code + " (vence en 10 minutos)";
        if (channel == OtpChannel.SMS) {
            smsPort.send(req.identifier(), message);
        } else {
            emailPort.send(req.identifier(), "Tu código de verificación InsightBloom", message);
        }
    }
}
