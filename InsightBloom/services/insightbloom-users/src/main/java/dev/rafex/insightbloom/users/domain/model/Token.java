package dev.rafex.insightbloom.users.domain.model;

import java.time.Instant;
import java.util.UUID;

public class Token {
    private final String uuid;
    private final String userUuid;
    private final String guestUserUuid;
    private final TokenKind tokenKind;
    private final String tokenValue;
    private final Instant expiresAt;
    private final Instant createdAt;
    private Instant revokedAt;

    public Token(String userUuid, String guestUserUuid, TokenKind tokenKind, String tokenValue, Instant expiresAt) {
        this.uuid = UUID.randomUUID().toString();
        this.userUuid = userUuid;
        this.guestUserUuid = guestUserUuid;
        this.tokenKind = tokenKind;
        this.tokenValue = tokenValue;
        this.expiresAt = expiresAt;
        this.createdAt = Instant.now();
    }

    public Token(String uuid, String userUuid, String guestUserUuid, TokenKind tokenKind,
                 String tokenValue, Instant expiresAt, Instant createdAt, Instant revokedAt) {
        this.uuid = uuid;
        this.userUuid = userUuid;
        this.guestUserUuid = guestUserUuid;
        this.tokenKind = tokenKind;
        this.tokenValue = tokenValue;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
        this.revokedAt = revokedAt;
    }

    public boolean isValid() {
        return revokedAt == null && Instant.now().isBefore(expiresAt);
    }

    public String getUuid() { return uuid; }
    public String getUserUuid() { return userUuid; }
    public String getGuestUserUuid() { return guestUserUuid; }
    public TokenKind getTokenKind() { return tokenKind; }
    public String getTokenValue() { return tokenValue; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getRevokedAt() { return revokedAt; }
}
