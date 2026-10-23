package dev.rafex.insightbloom.users.domain.model;

import java.time.Instant;

public record TicketDeviceCredential(String uuid, String ticketUuid, String publicKeyJwk,
                                     String label, String fingerprint, Instant registeredAt,
                                     Instant revokedAt) {
    public boolean active() { return revokedAt == null; }
}
