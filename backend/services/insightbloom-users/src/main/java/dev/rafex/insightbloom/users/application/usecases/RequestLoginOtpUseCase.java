package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.AuthMethod;
import dev.rafex.insightbloom.users.domain.model.OtpChannel;
import dev.rafex.insightbloom.users.domain.model.OtpCode;
import dev.rafex.insightbloom.users.domain.model.User;
import dev.rafex.insightbloom.users.domain.model.UserStatus;
import dev.rafex.insightbloom.users.domain.ports.EmailPort;
import dev.rafex.insightbloom.users.domain.ports.OtpCodeRepository;
import dev.rafex.insightbloom.users.domain.ports.UserRepository;
import dev.rafex.insightbloom.users.domain.services.OtpEmailTemplate;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Pide un codigo de login por correo. NO reutiliza {@link SendOtpUseCase} a proposito: ese caso
 * de uso sigue siendo del flujo de verificacion de registro (cualquier identificador con formato
 * valido puede pedir un codigo ahi) y no debe adquirir la regla adicional de este flujo --
 * "solo si authMethod == OTP_EMAIL" -- porque rompería la verificacion de cuentas nuevas, que
 * arrancan en PASSWORD por defecto.
 */
public class RequestLoginOtpUseCase {
    private static final int MAX_SENDS_PER_WINDOW = 3;
    private static final Duration RATE_LIMIT_WINDOW = Duration.ofHours(1);
    private static final Duration CODE_TTL = Duration.ofMinutes(10);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final OtpCodeRepository otpCodeRepository;
    private final EmailPort emailPort;

    public RequestLoginOtpUseCase(final UserRepository userRepository, final OtpCodeRepository otpCodeRepository,
                                   final EmailPort emailPort) {
        this.userRepository = userRepository;
        this.otpCodeRepository = otpCodeRepository;
        this.emailPort = emailPort;
    }

    public record Request(String identifier) {}

    /**
     * Siempre "tiene exito" desde el punto de vista del llamador -- nunca revela si el
     * identificador existe, si la cuenta usa OTP_EMAIL, o si se alcanzo el limite de envios.
     * El envio real del correo solo ocurre puertas adentro cuando todo eso se cumple.
     */
    public void execute(final Request request) {
        if (request == null || request.identifier() == null || request.identifier().isBlank()) {
            throw new IllegalArgumentException("identifier_required");
        }
        findByIdentifier(request.identifier()).ifPresent(this::sendIfEligible);
    }

    private void sendIfEligible(final User user) {
        if (user.getStatus() != UserStatus.ACTIVE) return;
        if (user.getAuthMethod() != AuthMethod.OTP_EMAIL) return;
        final String email = user.getEmail();
        if (email == null || email.isBlank()) return;
        if (!emailPort.isEnabled()) return;

        // Rate limit de envio: cuenta codigos generados para este email en la ultima hora,
        // usando la propia tabla de otp_codes (sin necesitar una tabla aparte de intentos).
        final int recentSends = otpCodeRepository.countSince(email, Instant.now().minus(RATE_LIMIT_WINDOW));
        if (recentSends >= MAX_SENDS_PER_WINDOW) return;

        final String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        final OtpCode otpCode = new OtpCode(email, OtpChannel.EMAIL, code, Instant.now().plus(CODE_TTL));
        otpCodeRepository.save(otpCode);
        emailPort.sendHtml(email, "Tu código de acceso a InsightBloom", OtpEmailTemplate.render(code));
    }

    private Optional<User> findByIdentifier(final String identifier) {
        return userRepository.findByUsername(identifier).or(() -> userRepository.findByEmail(identifier));
    }
}
