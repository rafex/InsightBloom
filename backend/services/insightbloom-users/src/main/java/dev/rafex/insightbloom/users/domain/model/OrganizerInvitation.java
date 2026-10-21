package dev.rafex.insightbloom.users.domain.model;

import java.time.Instant;

public record OrganizerInvitation(
        String uuid, String email, String status, String issuerUuid, String referralUuid,
        Instant createdAt, Instant expiresAt, Instant sentAt, Instant acceptedAt,
        String deliveryError) {}
