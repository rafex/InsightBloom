package dev.rafex.insightbloom.users.domain.model;

import java.time.Instant;

public record TicketDeviceEnrollment(String uuid, String ticketUuid, String tokenHash, String method,
                                     String otpHash, Instant expiresAt, Instant consumedAt,
                                     int failedAttempts, Instant createdAt) {}
