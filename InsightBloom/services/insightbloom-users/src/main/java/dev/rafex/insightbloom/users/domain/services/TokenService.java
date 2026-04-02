package dev.rafex.insightbloom.users.domain.services;

import dev.rafex.insightbloom.users.domain.model.Token;
import dev.rafex.insightbloom.users.domain.model.TokenKind;
import dev.rafex.insightbloom.users.domain.ports.TokenRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

public class TokenService {
    private final TokenRepository tokenRepository;

    public TokenService(TokenRepository tokenRepository) {
        this.tokenRepository = tokenRepository;
    }

    public Token issueUserToken(String userUuid, TokenKind kind) {
        String value = generateTokenValue();
        Instant expiresAt = Instant.now().plus(24, ChronoUnit.HOURS);
        Token token = new Token(userUuid, null, kind, value, expiresAt);
        tokenRepository.save(token);
        return token;
    }

    public Token issueGuestToken(String guestUserUuid) {
        String value = generateTokenValue();
        Instant expiresAt = Instant.now().plus(8, ChronoUnit.HOURS);
        Token token = new Token(null, guestUserUuid, TokenKind.GUEST, value, expiresAt);
        tokenRepository.save(token);
        return token;
    }

    public Optional<Token> validate(String tokenValue) {
        return tokenRepository.findByValue(tokenValue)
                .filter(Token::isValid);
    }

    private String generateTokenValue() {
        return UUID.randomUUID().toString().replace("-", "") +
               UUID.randomUUID().toString().replace("-", "");
    }
}
