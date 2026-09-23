package dev.rafex.insightbloom.users.domain.model;

import java.time.Instant;
import java.util.UUID;

/** Internal OTP request trace. Deliberately excludes the submitted identifier and OTP value. */
public record OtpRequestAudit(
        String uuid,
        Instant requestedAt,
        String accountUuid,
        String clientIp,
        String userAgent,
        String outcome) {

    public OtpRequestAudit(final Instant requestedAt, final String accountUuid, final String clientIp,
                           final String userAgent, final String outcome) {
        this(UUID.randomUUID().toString(), requestedAt, accountUuid, clientIp, userAgent, outcome);
    }
}
