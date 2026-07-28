package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.AuthMethod;
import dev.rafex.insightbloom.users.domain.model.Token;
import dev.rafex.insightbloom.users.domain.model.TokenKind;
import dev.rafex.insightbloom.users.domain.model.User;
import dev.rafex.insightbloom.users.domain.model.UserStatus;
import dev.rafex.insightbloom.users.domain.ports.PlatformSettingsRepository;
import dev.rafex.insightbloom.users.domain.ports.UserRepository;
import dev.rafex.insightbloom.users.domain.services.OtpLoginRequiredException;
import dev.rafex.insightbloom.users.domain.services.PasswordService;
import dev.rafex.insightbloom.users.domain.services.PlatformDeviceBlockedException;
import dev.rafex.insightbloom.users.domain.services.PlatformDeviceGuard;
import dev.rafex.insightbloom.users.domain.services.TokenService;

import java.time.Instant;
import java.util.Optional;

public class LoginUseCase {
    private final UserRepository userRepository;
    private final TokenService tokenService;
    private final PasswordService passwordService;
    private final PlatformDeviceGuard platformDeviceGuard;
    private final PlatformSettingsRepository platformSettingsRepository;

    public LoginUseCase(final UserRepository userRepository, final TokenService tokenService,
                        final PasswordService passwordService, final PlatformDeviceGuard platformDeviceGuard,
                        final PlatformSettingsRepository platformSettingsRepository) {
        this.userRepository = userRepository;
        this.tokenService = tokenService;
        this.passwordService = passwordService;
        this.platformDeviceGuard = platformDeviceGuard;
        this.platformSettingsRepository = platformSettingsRepository;
    }

    public record LoginRequest(String username, String password, String deviceFingerprint) {}
    public record LoginResult(String token, String userUuid, String role, String expiresAt) {}

    public Optional<LoginResult> execute(final LoginRequest request) {
        if (request == null) return Optional.empty();
        if (request.username() == null || request.username().isBlank()) return Optional.empty();
        if (request.password() == null || request.password().isBlank()) return Optional.empty();

        final Optional<User> user = findByIdentifier(request.username());
        return user.flatMap(u -> {
            if (u.getStatus() != UserStatus.ACTIVE) return Optional.empty();
            // La cuenta activo el login por codigo (ver SetAuthMethodUseCase): la contrasena ya
            // no es un metodo de acceso valido, sin importar si coincide o no. Usar
            // /auth/login/otp/request + /verify en su lugar.
            if (u.getAuthMethod() == AuthMethod.OTP_EMAIL) throw new OtpLoginRequiredException();
            if (u.getPasswordHash() == null || u.getPasswordHash().isBlank()) return Optional.empty();
            if (!passwordService.verify(request.password(), u.getPasswordHash())) return Optional.empty();
            // Transparently upgrade SHA-256 hashes to PBKDF2 on first successful login
            if (passwordService.isLegacyHash(u.getPasswordHash())) {
                u.setPasswordHash(passwordService.hash(request.password()));
            }
            u.setLastLoginAt(Instant.now());
            userRepository.save(u);
            final Token token = tokenService.issueUserToken(u.getUuid(), TokenKind.USER, request.deviceFingerprint());

            final var access = platformDeviceGuard.checkAndRegisterLogin(
                    request.deviceFingerprint(), u.getUuid(), TokenKind.USER, platformSettingsRepository.get());
            if (access instanceof PlatformDeviceGuard.Result.Blocked) {
                throw new PlatformDeviceBlockedException();
            }

            return Optional.of(new LoginResult(token.getTokenValue(), u.getUuid(),
                    dev.rafex.insightbloom.users.domain.model.UserRole.toCsv(u.getRoles()),
                    token.getExpiresAt().toString()));
        });
    }

    private Optional<User> findByIdentifier(final String identifier) {
        return userRepository.findByUsername(identifier)
                .or(() -> userRepository.findByEmail(identifier));
    }
}
