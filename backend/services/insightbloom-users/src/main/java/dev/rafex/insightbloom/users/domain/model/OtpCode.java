package dev.rafex.insightbloom.users.domain.model;

import java.time.Instant;
import java.util.UUID;

public class OtpCode {
    private final String uuid;
    private final String identifier;
    private final OtpChannel channel;
    private final String code;
    private final Instant expiresAt;
    private boolean consumed;
    private final Instant createdAt;

    public OtpCode(final String identifier, final OtpChannel channel, final String code,
                   final Instant expiresAt) {
        this.uuid = UUID.randomUUID().toString();
        this.identifier = identifier;
        this.channel = channel;
        this.code = code;
        this.expiresAt = expiresAt;
        this.consumed = false;
        this.createdAt = Instant.now();
    }

    public OtpCode(final String uuid, final String identifier, final OtpChannel channel, final String code,
                   final Instant expiresAt, final boolean consumed, final Instant createdAt) {
        this.uuid = uuid;
        this.identifier = identifier;
        this.channel = channel;
        this.code = code;
        this.expiresAt = expiresAt;
        this.consumed = consumed;
        this.createdAt = createdAt;
    }

    public boolean isValid(final String candidateCode) {
        return !consumed && Instant.now().isBefore(expiresAt) && code.equals(candidateCode);
    }

    public String getUuid() { return uuid; }
    public String getIdentifier() { return identifier; }
    public OtpChannel getChannel() { return channel; }
    public String getCode() { return code; }
    public Instant getExpiresAt() { return expiresAt; }
    public boolean isConsumed() { return consumed; }
    public Instant getCreatedAt() { return createdAt; }
}
