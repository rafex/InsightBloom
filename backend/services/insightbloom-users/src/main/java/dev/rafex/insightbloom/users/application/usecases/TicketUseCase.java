package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.Conference;
import dev.rafex.insightbloom.users.domain.model.ConferenceMembership;
import dev.rafex.insightbloom.users.domain.model.EventCapability;
import dev.rafex.insightbloom.users.domain.model.Ticket;
import dev.rafex.insightbloom.users.domain.ports.ConferenceMembershipRepository;
import dev.rafex.insightbloom.users.domain.ports.ConferenceRepository;
import dev.rafex.insightbloom.users.domain.ports.EmailPort;
import dev.rafex.insightbloom.users.domain.ports.EventTypeRepository;
import dev.rafex.insightbloom.users.domain.ports.TicketRepository;
import dev.rafex.insightbloom.users.domain.ports.ReservationRepository;
import dev.rafex.insightbloom.users.domain.ports.TimezoneRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Emisión, canje y check-in de boletos. Las transiciones sensibles son atómicas en SQLite. */
public class TicketUseCase {
    private static final long TICKET_EXPIRATION_HOURS = 5;
    private static final Pattern UUID_V4 = Pattern.compile("(?i)([0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12})");
    private final ConferenceRepository conferenceRepository;
    private final EventTypeRepository eventTypeRepository;
    private final TicketRepository ticketRepository;
    private final ReservationRepository reservationRepository;
    private final ConferenceMembershipRepository membershipRepository;
    private final EmailPort emailPort;
    private final String frontendBaseUrl;
    private final TimezoneRepository timezoneRepository;

    public TicketUseCase(final ConferenceRepository conferenceRepository, final EventTypeRepository eventTypeRepository,
                         final TicketRepository ticketRepository, final ConferenceMembershipRepository membershipRepository,
                         final EmailPort emailPort, final String frontendBaseUrl,
                         final ReservationRepository reservationRepository) {
        this(conferenceRepository, eventTypeRepository, ticketRepository, membershipRepository, emailPort,
                frontendBaseUrl, reservationRepository, null);
    }

    public TicketUseCase(final ConferenceRepository conferenceRepository, final EventTypeRepository eventTypeRepository,
                         final TicketRepository ticketRepository, final ConferenceMembershipRepository membershipRepository,
                         final EmailPort emailPort, final String frontendBaseUrl,
                         final ReservationRepository reservationRepository, final TimezoneRepository timezoneRepository) {
        this.conferenceRepository = conferenceRepository;
        this.eventTypeRepository = eventTypeRepository;
        this.ticketRepository = ticketRepository;
        this.reservationRepository = reservationRepository;
        this.membershipRepository = membershipRepository;
        this.emailPort = emailPort;
        this.frontendBaseUrl = frontendBaseUrl;
        this.timezoneRepository = timezoneRepository;
    }

    public Ticket issue(final String conferenceUuid, final String issuerUuid, final String recipientEmail,
                        final String seatUuid) {
        final Conference conference = conference(conferenceUuid);
        ensureConferenceActive(conference);
        final EventCapability capability = seatUuid == null ? EventCapability.TICKETING_GENERAL : EventCapability.TICKETING_SEATED;
        if (!hasCapability(conference, capability)) throw new IllegalStateException("capability_not_available");
        if ("SEATED".equals(conference.getSeatingMode()) && seatUuid == null) throw new IllegalStateException("seat_required");
        if ("SEATED".equals(conference.getSeatingMode()) == false && seatUuid != null) throw new IllegalStateException("seat_not_allowed");
        final boolean counted = conference.getCapacity() != null;
        if (counted && !conferenceRepository.tryIncrementReservedCount(conferenceUuid)) throw new IllegalStateException("capacity_exceeded");
        final Ticket ticket = new Ticket(conferenceUuid, issuerUuid, blankToNull(recipientEmail), blankToNull(seatUuid));
        try {
            ticketRepository.insert(ticket);
        } catch (RuntimeException e) {
            if (counted) conferenceRepository.decrementReservedCount(conferenceUuid);
            throw e;
        }
        sendEmail(conference, ticket);
        return ticket;
    }

    public Ticket claim(final String conferenceUuid, final String qrOrUuid, final String userUuid) {
        final Conference conference = conference(conferenceUuid);
        expireIfNeeded(conference);
        final String code = normalizeCode(qrOrUuid);
        final Ticket ticket = ticketRepository.findByCode(conferenceUuid, code)
                .orElseThrow(() -> new IllegalArgumentException("ticket_not_found"));
        if (ticket.getStatus().name().equals("REVOKED")) throw new IllegalStateException("ticket_revoked");
        if (ticket.getStatus().name().equals("EXPIRED")) throw new IllegalStateException("ticket_expired");
        if (ticket.getClaimedByUserUuid() != null) {
            if (ticket.getClaimedByUserUuid().equals(userUuid)) return ticket;
            throw new IllegalStateException("ticket_already_claimed");
        }
        if (!ticketRepository.claim(ticket.getUuid(), userUuid, Instant.now().toString())) {
            final Ticket current = ticketRepository.findByUuid(ticket.getUuid()).orElseThrow(() -> new IllegalArgumentException("ticket_not_found"));
            if (userUuid.equals(current.getClaimedByUserUuid())) return current;
            throw new IllegalStateException("ticket_already_claimed");
        }
        if (!membershipRepository.exists(userUuid, conferenceUuid)) {
            membershipRepository.recordJoin(new ConferenceMembership(userUuid, conferenceUuid, conference.getName(), conference.getFriendlyId()));
        }
        return ticketRepository.findByUuid(ticket.getUuid()).orElseThrow(() -> new IllegalArgumentException("ticket_not_found"));
    }

    public Optional<Ticket> myTicket(final String conferenceUuid, final String userUuid) {
        final Conference conference = conference(conferenceUuid);
        expireIfNeeded(conference);
        return ticketRepository.findByConferenceAndUser(conferenceUuid, userUuid);
    }

    public List<Ticket> list(final String conferenceUuid) {
        final Conference conference = conference(conferenceUuid);
        expireIfNeeded(conference);
        return ticketRepository.findByConference(conferenceUuid);
    }

    public Ticket checkIn(final String conferenceUuid, final String qrOrUuid) {
        final Conference conference = conference(conferenceUuid);
        expireIfNeeded(conference);
        final String code = normalizeCode(qrOrUuid);
        final Ticket ticket = ticketRepository.findByCode(conferenceUuid, code)
                .or(() -> ticketRepository.findByUuid(code))
                .orElseThrow(() -> new IllegalArgumentException("ticket_not_found"));
        if (!conferenceUuid.equals(ticket.getConferenceUuid())) throw new IllegalArgumentException("ticket_not_found");
        if (ticket.getStatus().name().equals("CHECKED_IN")) throw new IllegalStateException("already_checked_in");
        if (ticket.getStatus().name().equals("EXPIRED")) throw new IllegalStateException("ticket_expired");
        if (ticket.getStatus().name().equals("ISSUED")) throw new IllegalStateException("ticket_not_claimed");
        if (!ticketRepository.checkIn(ticket.getUuid(), Instant.now().toString())) throw new IllegalStateException("already_checked_in");
        return ticketRepository.findByUuid(ticket.getUuid()).orElseThrow(() -> new IllegalArgumentException("ticket_not_found"));
    }

    public Ticket revoke(final String conferenceUuid, final String ticketUuid) {
        expireIfNeeded(conference(conferenceUuid));
        final Ticket ticket = ticketRepository.findByUuid(ticketUuid)
                .filter(t -> conferenceUuid.equals(t.getConferenceUuid()))
                .orElseThrow(() -> new IllegalArgumentException("ticket_not_found"));
        if (!ticketRepository.revoke(ticketUuid)) throw new IllegalStateException("ticket_already_used");
        return ticketRepository.findByUuid(ticketUuid).orElse(ticket);
    }

    public boolean hasAccess(final Conference conference, final String userUuid) {
        if (!isTicketed(conference)) return true;
        if (conference.getExpiresAt() != null && conference.getExpiresAt().isBefore(Instant.now())) {
            expireIfNeeded(conference);
            return false;
        }
        expireIfNeeded(conference);
        // En un evento ticketed, la reserva sólo aparta aforo; el acceso efectivo depende de un
        // boleto CLAIMED/CHECKED_IN vigente. Mantener la reserva como alternativa permitía que un
        // usuario conservara acceso después de que el moderador revocara su boleto.
        return ticketRepository.findByConferenceAndUser(conference.getUuid(), userUuid).isPresent();
    }

    public boolean isTicketed(final Conference conference) {
        return hasCapability(conference, EventCapability.TICKETING_GENERAL) || hasCapability(conference, EventCapability.TICKETING_SEATED);
    }

    /** Marca automáticamente como EXPIRED los boletos vencidos de eventos ticketed. */
    public int expireTickets(final Instant now) {
        int expiredConferences = 0;
        for (final Conference conference : conferenceRepository.findAll()) {
            if (!isTicketed(conference)) continue;
            final Instant expirationAt = ticketExpirationAt(conference);
            if (expirationAt != null && !expirationAt.isAfter(now)) {
                ticketRepository.expireByConference(conference.getUuid(), expirationAt.toString());
                expiredConferences++;
            }
        }
        return expiredConferences;
    }

    public static String normalizeCode(final String input) {
        if (input == null || input.isBlank()) throw new IllegalArgumentException("ticket_required");
        final Matcher matcher = UUID_V4.matcher(input.trim());
        if (!matcher.find()) throw new IllegalArgumentException("ticket_invalid_format");
        return UUID.fromString(matcher.group(1)).toString();
    }

    private Conference conference(final String uuid) {
        return conferenceRepository.findByUuid(uuid).orElseThrow(() -> new IllegalArgumentException("conference_not_found"));
    }

    private void ensureConferenceActive(final Conference conference) {
        final Instant expirationAt = ticketExpirationAt(conference);
        if (expirationAt != null && !expirationAt.isAfter(Instant.now())) {
            throw new IllegalStateException("conference_expired");
        }
    }

    private void expireIfNeeded(final Conference conference) {
        final Instant expirationAt = ticketExpirationAt(conference);
        if (expirationAt != null && !expirationAt.isAfter(Instant.now())) {
            ticketRepository.expireByConference(conference.getUuid(), expirationAt.toString());
        }
    }

    private Instant ticketExpirationAt(final Conference conference) {
        Instant expirationAt = conference.getExpiresAt();
        if (conference.getEventDate() != null && conference.getStartTime() != null) {
            try {
                final LocalDate eventDate = LocalDate.parse(conference.getEventDate());
                final LocalTime startTime = LocalTime.parse(conference.getStartTime());
                final int offsetMinutes = conference.getTimezoneId() == null || timezoneRepository == null
                        ? -360
                        : timezoneRepository.findById(conference.getTimezoneId())
                        .map(t -> t.utcOffsetMinutes()).orElse(-360);
                final Instant eventExpiration = eventDate.atTime(startTime)
                        .toInstant(ZoneOffset.ofTotalSeconds(offsetMinutes * 60))
                        .plusSeconds(TICKET_EXPIRATION_HOURS * 60 * 60);
                if (expirationAt == null || eventExpiration.isBefore(expirationAt)) expirationAt = eventExpiration;
            } catch (final RuntimeException ignored) {
                // Datos de calendario incompletos o inválidos: conserva expiresAt si existe.
            }
        }
        return expirationAt;
    }

    private boolean hasCapability(final Conference conference, final EventCapability capability) {
        return eventTypeRepository.findByKey(conference.getEventTypeKey()).map(t -> t.isActive() && t.hasCapability(capability)).orElse(false);
    }

    private void sendEmail(final Conference conference, final Ticket ticket) {
        if (!emailPort.isEnabled() || ticket.getRecipientEmail() == null) return;
        try {
            emailPort.send(ticket.getRecipientEmail(), "Tu boleto para " + conference.getName(),
                    "Tu boleto está listo. Canjéalo o preséntalo en la entrada:\n\n" +
                    frontendBaseUrl + "/c/" + conference.getFriendlyId() + "/ticket?ticket=" + ticket.getTicketCode() +
                    "\n\nCódigo: " + ticket.getTicketCode());
        } catch (Exception ignored) { }
    }

    private static String blankToNull(final String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
