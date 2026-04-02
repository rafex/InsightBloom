package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.Token;
import dev.rafex.insightbloom.users.domain.model.TokenKind;
import dev.rafex.insightbloom.users.domain.model.User;
import dev.rafex.insightbloom.users.domain.ports.UserRepository;
import dev.rafex.insightbloom.users.domain.services.TokenService;

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
        // PoC: password not validated (no password storage in PoC schema)
        Optional<User> user = userRepository.findByUsername(request.username());
        return user.map(u -> {
            Token token = tokenService.issueUserToken(u.getUuid(), TokenKind.USER);
            return new LoginResult(token.getTokenValue(), u.getUuid(), u.getRole().name().toLowerCase());
        });
    }
}
