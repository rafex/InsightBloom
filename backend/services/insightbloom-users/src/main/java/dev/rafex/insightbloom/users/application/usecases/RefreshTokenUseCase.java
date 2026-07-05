package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.services.TokenService;

import java.util.Optional;

public class RefreshTokenUseCase {
    private final TokenService tokenService;
    private final ValidateTokenUseCase validateTokenUseCase;

    public RefreshTokenUseCase(final TokenService tokenService, final ValidateTokenUseCase validateTokenUseCase) {
        this.tokenService = tokenService;
        this.validateTokenUseCase = validateTokenUseCase;
    }

    public record RefreshResult(String token, String role, String expiresAt) {}

    public Optional<RefreshResult> execute(final String oldTokenValue) {
        return tokenService.reissue(oldTokenValue).map(newToken -> {
            final var validation = validateTokenUseCase.execute(newToken.getTokenValue());
            return new RefreshResult(newToken.getTokenValue(), validation.role(), validation.expiresAt());
        });
    }
}
