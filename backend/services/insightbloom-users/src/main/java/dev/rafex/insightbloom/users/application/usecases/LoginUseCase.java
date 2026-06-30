package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.Token;
import dev.rafex.insightbloom.users.domain.model.TokenKind;
import dev.rafex.insightbloom.users.domain.model.User;
import dev.rafex.insightbloom.users.domain.model.UserStatus;
import dev.rafex.insightbloom.users.domain.ports.UserRepository;
import dev.rafex.insightbloom.users.domain.services.PasswordService;
import dev.rafex.insightbloom.users.domain.services.TokenService;

import java.util.Optional;

public class LoginUseCase {
    private final UserRepository userRepository;
    private final TokenService tokenService;
    private final PasswordService passwordService;

    public LoginUseCase(final UserRepository userRepository, final TokenService tokenService,
                        final PasswordService passwordService) {
        this.userRepository = userRepository;
        this.tokenService = tokenService;
        this.passwordService = passwordService;
    }

    public record LoginRequest(String username, String password) {}
    public record LoginResult(String token, String userUuid, String role) {}

    public Optional<LoginResult> execute(final LoginRequest request) {
        if (request == null) return Optional.empty();
        if (request.username() == null || request.username().isBlank()) return Optional.empty();
        if (request.password() == null || request.password().isBlank()) return Optional.empty();

        final Optional<User> user = findByIdentifier(request.username());
        return user.flatMap(u -> {
            if (u.getStatus() != UserStatus.ACTIVE) return Optional.empty();
            if (u.getPasswordHash() == null || u.getPasswordHash().isBlank()) return Optional.empty();
            if (!passwordService.verify(request.password(), u.getPasswordHash())) return Optional.empty();
            // Transparently upgrade SHA-256 hashes to PBKDF2 on first successful login
            if (passwordService.isLegacyHash(u.getPasswordHash())) {
                u.setPasswordHash(passwordService.hash(request.password()));
                userRepository.save(u);
            }
            final Token token = tokenService.issueUserToken(u.getUuid(), TokenKind.USER);
            return Optional.of(new LoginResult(token.getTokenValue(), u.getUuid(),
                    dev.rafex.insightbloom.users.domain.model.UserRole.toCsv(u.getRoles())));
        });
    }

    private Optional<User> findByIdentifier(final String identifier) {
        return userRepository.findByUsername(identifier)
                .or(() -> userRepository.findByEmail(identifier));
    }
}
