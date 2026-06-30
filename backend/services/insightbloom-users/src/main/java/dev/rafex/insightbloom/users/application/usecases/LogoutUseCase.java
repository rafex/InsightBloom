package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.services.TokenService;

public class LogoutUseCase {
    private final TokenService tokenService;

    public LogoutUseCase(final TokenService tokenService) {
        this.tokenService = tokenService;
    }

    public void execute(final String tokenValue) {
        tokenService.revokeToken(tokenValue);
    }
}
