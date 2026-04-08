package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.Token;
import dev.rafex.insightbloom.users.domain.model.TokenKind;
import dev.rafex.insightbloom.users.domain.model.User;
import dev.rafex.insightbloom.users.domain.ports.UserRepository;
import dev.rafex.insightbloom.users.domain.services.TokenService;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

public class LoginUseCase {
    private final UserRepository userRepository;
    private final TokenService tokenService;

    public LoginUseCase(UserRepository userRepository, TokenService tokenService) {
        this.userRepository = userRepository;
        this.tokenService = tokenService;
    }

    public record LoginRequest(String username, String password) {}
    public record LoginResult(String token, String userUuid, String role) {}

    public Optional<LoginResult> execute(LoginRequest request) {
        Optional<User> user = userRepository.findByUsername(request.username());
        return user.flatMap(u -> {
            // If user has a password hash, validate it; otherwise allow (seed users without password)
            if (u.getPasswordHash() != null && !u.getPasswordHash().isBlank()) {
                String inputHash = sha256(request.password() != null ? request.password() : "");
                if (!inputHash.equals(u.getPasswordHash())) return Optional.empty();
            }
            Token token = tokenService.issueUserToken(u.getUuid(), TokenKind.USER);
            return Optional.of(new LoginResult(token.getTokenValue(), u.getUuid(), u.getRole().name().toLowerCase()));
        });
    }

    public static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
