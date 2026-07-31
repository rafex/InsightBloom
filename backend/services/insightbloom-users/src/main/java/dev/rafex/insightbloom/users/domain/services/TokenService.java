package dev.rafex.insightbloom.users.domain.services;

import dev.rafex.insightbloom.users.domain.model.Token;
import dev.rafex.insightbloom.users.domain.model.TokenKind;
import dev.rafex.insightbloom.users.domain.ports.TokenRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

public class TokenService {
    private static final long SESSION_TOKEN_TTL_HOURS = 1;
    private final TokenRepository tokenRepository;

    public TokenService(TokenRepository tokenRepository) {
        this.tokenRepository = tokenRepository;
    }

    public Token issueUserToken(String userUuid, TokenKind kind) {
        return issueUserToken(userUuid, kind, null);
    }

    public Token issueUserToken(String userUuid, TokenKind kind, String deviceFingerprint) {
        String value = generateTokenValue();
        Instant expiresAt = Instant.now().plus(SESSION_TOKEN_TTL_HOURS, ChronoUnit.HOURS);
        Token token = new Token(userUuid, null, kind, value, expiresAt, deviceFingerprint);
        tokenRepository.save(token);
        return token;
    }

    public Token issueGuestToken(String guestUserUuid) {
        return issueGuestToken(guestUserUuid, null);
    }

    public Token issueGuestToken(String guestUserUuid, String deviceFingerprint) {
        String value = generateTokenValue();
        Instant expiresAt = Instant.now().plus(SESSION_TOKEN_TTL_HOURS, ChronoUnit.HOURS);
        Token token = new Token(null, guestUserUuid, TokenKind.GUEST, value, expiresAt, deviceFingerprint);
        tokenRepository.save(token);
        return token;
    }

    public Optional<Token> validate(String tokenValue) {
        return tokenRepository.findByValue(tokenValue)
                .filter(Token::isValid);
    }

    /** Renueva un token vigente: revoca el actual y emite uno nuevo del mismo sujeto/tipo. */
    public Optional<Token> reissue(String tokenValue) {
        return validate(tokenValue).map(current -> {
            tokenRepository.revokeByValue(tokenValue);
            if (current.getTokenKind() == TokenKind.GUEST) {
                return issueGuestToken(current.getGuestUserUuid(), current.getDeviceFingerprint());
            }
            return issueUserToken(current.getUserUuid(), current.getTokenKind(), current.getDeviceFingerprint());
        });
    }

    public void revokeAllForUser(String userUuid) {
        tokenRepository.revokeAllForUser(userUuid);
    }

    public void revokeToken(String tokenValue) {
        tokenRepository.revokeByValue(tokenValue);
    }

    private String generateTokenValue() {
        return UUID.randomUUID().toString().replace("-", "") +
               UUID.randomUUID().toString().replace("-", "");
    }
}
