package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.AuthMethod;
import dev.rafex.insightbloom.users.domain.model.OtpCode;
import dev.rafex.insightbloom.users.domain.model.Token;
import dev.rafex.insightbloom.users.domain.model.TokenKind;
import dev.rafex.insightbloom.users.domain.model.User;
import dev.rafex.insightbloom.users.domain.model.UserRole;
import dev.rafex.insightbloom.users.domain.model.UserStatus;
import dev.rafex.insightbloom.users.domain.ports.OtpCodeRepository;
import dev.rafex.insightbloom.users.domain.ports.UserRepository;
import dev.rafex.insightbloom.users.domain.services.TokenService;

import java.time.Instant;
import java.util.Optional;

/**
 * Verifica el codigo de login y emite sesion -- separado de {@link VerifyOtpUseCase} (que sigue
 * siendo del flujo de verificacion de registro) por la misma razon que {@link RequestLoginOtpUseCase}:
 * este exige ademas que la cuenta tenga {@code AuthMethod.OTP_EMAIL} activo.
 */
public class VerifyLoginOtpUseCase {
    /** Codigo de 6 digitos = 1,000,000 combinaciones; sin este limite, 10 minutos de ventana sin
     *  tope de intentos son fuerza bruta acotada pero no despreciable. */
    private static final int MAX_FAILED_ATTEMPTS = 2;
    private static final String GENERIC_ERROR = "otp_invalid_or_expired";

    private final UserRepository userRepository;
    private final OtpCodeRepository otpCodeRepository;
    private final TokenService tokenService;

    public VerifyLoginOtpUseCase(final UserRepository userRepository, final OtpCodeRepository otpCodeRepository,
                                  final TokenService tokenService) {
        this.userRepository = userRepository;
        this.otpCodeRepository = otpCodeRepository;
        this.tokenService = tokenService;
    }

    public record Request(String identifier, String code, String deviceFingerprint) {}
    public record Result(String token, String userUuid, String role, String expiresAt) {}

    public Result execute(final Request request) {
        if (request == null || request.identifier() == null || request.identifier().isBlank()
                || request.code() == null || request.code().isBlank()) {
            throw new IllegalArgumentException(GENERIC_ERROR);
        }
        // Mismo mensaje generico para "no existe"/"no usa OTP"/"codigo invalido" -- no distinguir
        // esos casos evita que este endpoint sirva para enumerar cuentas.
        final User user = findByIdentifier(request.identifier())
                .filter(u -> u.getStatus() == UserStatus.ACTIVE && u.getAuthMethod() == AuthMethod.OTP_EMAIL)
                .orElseThrow(() -> new IllegalArgumentException(GENERIC_ERROR));

        final OtpCode otpCode = otpCodeRepository.findLatestActive(user.getEmail())
                .orElseThrow(() -> new IllegalArgumentException(GENERIC_ERROR));

        if (otpCode.getFailedAttempts() >= MAX_FAILED_ATTEMPTS) {
            throw new IllegalArgumentException(GENERIC_ERROR);
        }
        if (!otpCode.isValid(request.code())) {
            otpCodeRepository.incrementFailedAttempts(otpCode.getUuid());
            throw new IllegalArgumentException(GENERIC_ERROR);
        }
        otpCodeRepository.markConsumed(otpCode.getUuid());

        user.setLastLoginAt(Instant.now());
        userRepository.save(user);
        final Token token = tokenService.issueUserToken(user.getUuid(), TokenKind.USER, request.deviceFingerprint());
        return new Result(token.getTokenValue(), user.getUuid(), UserRole.toCsv(user.getRoles()),
                token.getExpiresAt().toString());
    }

    private Optional<User> findByIdentifier(final String identifier) {
        return userRepository.findByUsername(identifier).or(() -> userRepository.findByEmail(identifier));
    }
}
