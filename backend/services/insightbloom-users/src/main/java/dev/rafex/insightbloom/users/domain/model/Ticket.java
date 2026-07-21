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
        this.status = TicketStatus.ISSUED;
        this.issuedAt = Instant.now();
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
        this.uuid = uuid;
        this.conferenceUuid = conferenceUuid;
        this.ticketCode = ticketCode;
        this.issuedByUserUuid = issuedByUserUuid;
        this.recipientEmail = recipientEmail;
        this.seatUuid = seatUuid;
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
    public TicketStatus getStatus() { return status; }
    public String getClaimedByUserUuid() { return claimedByUserUuid; }
    public Instant getIssuedAt() { return issuedAt; }
    public Instant getClaimedAt() { return claimedAt; }
    public Instant getCheckedInAt() { return checkedInAt; }
    public String getRevokedByUserUuid() { return revokedByUserUuid; }
    public Instant getRevokedAt() { return revokedAt; }
}
