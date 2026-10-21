package dev.rafex.insightbloom.users.domain.model;

import java.time.Instant;

public record OrganizerReferral(
        String uuid, String email, String status, String inviterUuid, String reviewerUuid,
        String invitationUuid, Instant createdAt, Instant reviewedAt) {}
