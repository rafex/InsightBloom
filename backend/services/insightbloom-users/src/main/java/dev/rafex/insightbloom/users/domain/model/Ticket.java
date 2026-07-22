package dev.rafex.insightbloom.users.domain.model;

import java.time.Instant;
import java.util.UUID;

/** Boleto emitido por el organizador. El ticketCode es el contenido del QR y un UUID v4. */
public class Ticket {
    private final String uuid;
    private final String conferenceUuid;
    private final String ticketCode;
    private final String issuedByUserUuid;
    private final String recipientEmail;
    private final String seatUuid;
    private final boolean operational;
    private TicketStatus status;
    private String claimedByUserUuid;
    private final Instant issuedAt;
    private Instant claimedAt;
    private Instant checkedInAt;
    private String revokedByUserUuid;
    private Instant revokedAt;

    public Ticket(final String conferenceUuid, final String issuedByUserUuid,
                  final String recipientEmail, final String seatUuid) {
        this.uuid = UUID.randomUUID().toString();
        this.conferenceUuid = conferenceUuid;
        this.ticketCode = UUID.randomUUID().toString();
        this.issuedByUserUuid = issuedByUserUuid;
        this.recipientEmail = recipientEmail;
        this.seatUuid = seatUuid;
        this.operational = false;
        this.status = TicketStatus.ISSUED;
        this.issuedAt = Instant.now();
    }

    /** Boleto reservado para un operador del evento. Consume aforo y no se puede revocar. */
    public static Ticket operational(final String conferenceUuid, final String issuedByUserUuid,
                                     final String operatorUserUuid) {
        final Instant now = Instant.now();
        final Ticket ticket = new Ticket(UUID.randomUUID().toString(), conferenceUuid,
                UUID.randomUUID().toString(), issuedByUserUuid, null, null,
                TicketStatus.CLAIMED, operatorUserUuid, now, now, null, null, null, true);
        return ticket;
    }

    public Ticket(final String uuid, final String conferenceUuid, final String ticketCode,
                  final String issuedByUserUuid, final String recipientEmail, final String seatUuid,
                  final TicketStatus status, final String claimedByUserUuid,
                  final Instant issuedAt, final Instant claimedAt, final Instant checkedInAt) {
        this(uuid, conferenceUuid, ticketCode, issuedByUserUuid, recipientEmail, seatUuid, status,
                claimedByUserUuid, issuedAt, claimedAt, checkedInAt, null, null);
    }

    public Ticket(final String uuid, final String conferenceUuid, final String ticketCode,
                  final String issuedByUserUuid, final String recipientEmail, final String seatUuid,
                  final TicketStatus status, final String claimedByUserUuid,
                  final Instant issuedAt, final Instant claimedAt, final Instant checkedInAt,
                  final String revokedByUserUuid, final Instant revokedAt) {
        this(uuid, conferenceUuid, ticketCode, issuedByUserUuid, recipientEmail, seatUuid, status,
                claimedByUserUuid, issuedAt, claimedAt, checkedInAt, revokedByUserUuid, revokedAt, false);
    }

    public Ticket(final String uuid, final String conferenceUuid, final String ticketCode,
                  final String issuedByUserUuid, final String recipientEmail, final String seatUuid,
                  final TicketStatus status, final String claimedByUserUuid,
                  final Instant issuedAt, final Instant claimedAt, final Instant checkedInAt,
                  final String revokedByUserUuid, final Instant revokedAt, final boolean operational) {
        this.uuid = uuid;
        this.conferenceUuid = conferenceUuid;
        this.ticketCode = ticketCode;
        this.issuedByUserUuid = issuedByUserUuid;
        this.recipientEmail = recipientEmail;
        this.seatUuid = seatUuid;
        this.operational = operational;
        this.status = status;
        this.claimedByUserUuid = claimedByUserUuid;
        this.issuedAt = issuedAt;
        this.claimedAt = claimedAt;
        this.checkedInAt = checkedInAt;
        this.revokedByUserUuid = revokedByUserUuid;
        this.revokedAt = revokedAt;
    }

    public String getUuid() { return uuid; }
    public String getConferenceUuid() { return conferenceUuid; }
    public String getTicketCode() { return ticketCode; }
    public String getIssuedByUserUuid() { return issuedByUserUuid; }
    public String getRecipientEmail() { return recipientEmail; }
    public String getSeatUuid() { return seatUuid; }
    public boolean isOperational() { return operational; }
    public TicketStatus getStatus() { return status; }
    public String getClaimedByUserUuid() { return claimedByUserUuid; }
    public Instant getIssuedAt() { return issuedAt; }
    public Instant getClaimedAt() { return claimedAt; }
    public Instant getCheckedInAt() { return checkedInAt; }
    public String getRevokedByUserUuid() { return revokedByUserUuid; }
    public Instant getRevokedAt() { return revokedAt; }
}
