package dev.rafex.insightbloom.users.domain.model;

import java.time.Instant;
import java.util.UUID;

public class Reservation {
    private final String uuid;
    private final String conferenceUuid;
    private final String userUuid;
    private final String seatUuid; // nullable, solo en modo SEATED
    private final String ticketCode;
    private ReservationStatus status;
    private final Instant createdAt;
    private Instant checkedInAt;

    public Reservation(final String conferenceUuid, final String userUuid, final String seatUuid) {
        this.uuid = UUID.randomUUID().toString();
        this.conferenceUuid = conferenceUuid;
        this.userUuid = userUuid;
        this.seatUuid = seatUuid;
        this.ticketCode = UUID.randomUUID().toString();
        this.status = ReservationStatus.RESERVED;
        this.createdAt = Instant.now();
    }

    public Reservation(final String uuid, final String conferenceUuid, final String userUuid, final String seatUuid,
                        final String ticketCode, final ReservationStatus status, final Instant createdAt,
                        final Instant checkedInAt) {
        this.uuid = uuid;
        this.conferenceUuid = conferenceUuid;
        this.userUuid = userUuid;
        this.seatUuid = seatUuid;
        this.ticketCode = ticketCode;
        this.status = status;
        this.createdAt = createdAt;
        this.checkedInAt = checkedInAt;
    }

    public String getUuid() { return uuid; }
    public String getConferenceUuid() { return conferenceUuid; }
    public String getUserUuid() { return userUuid; }
    public String getSeatUuid() { return seatUuid; }
    public String getTicketCode() { return ticketCode; }
    public ReservationStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getCheckedInAt() { return checkedInAt; }

    public void markCheckedIn() {
        this.status = ReservationStatus.CHECKED_IN;
        this.checkedInAt = Instant.now();
    }
}
