package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.Token;
import dev.rafex.insightbloom.users.domain.model.UserRole;
import dev.rafex.insightbloom.users.domain.ports.GuestUserRepository;
import dev.rafex.insightbloom.users.domain.ports.UserRepository;
import dev.rafex.insightbloom.users.domain.services.TokenService;

import java.util.Optional;

public class ValidateTokenUseCase {
    private final TokenService tokenService;
    private final UserRepository userRepository;
    private final GuestUserRepository guestUserRepository;

    public ValidateTokenUseCase(TokenService tokenService, UserRepository userRepository,
                                 GuestUserRepository guestUserRepository) {
        this.tokenService = tokenService;
        this.userRepository = userRepository;
        this.guestUserRepository = guestUserRepository;
    }

    public record ValidationResult(boolean valid, String subjectUuid, String kind, String role) {}

    public ValidationResult execute(String tokenValue) {
        Optional<Token> tokenOpt = tokenService.validate(tokenValue);
        if (tokenOpt.isEmpty()) {
            return new ValidationResult(false, null, null, null);
        }
        Token token = tokenOpt.get();

        return switch (token.getTokenKind()) {
            case USER -> userRepository.findByUuid(token.getUserUuid())
                    .map(u -> new ValidationResult(true, u.getUuid(), "user", u.getRole().name().toLowerCase()))
                    .orElse(new ValidationResult(false, null, null, null));
            case GUEST -> guestUserRepository.findByUuid(token.getGuestUserUuid())
                    .map(g -> new ValidationResult(true, g.getUuid(), "guest", "guest"))
                    .orElse(new ValidationResult(false, null, null, null));
            case WEBHOOK -> new ValidationResult(true, token.getUserUuid(), "webhook", "webhook");
        };
    }
}
