package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.Conference;
import dev.rafex.insightbloom.users.domain.model.ConferenceMembership;
import dev.rafex.insightbloom.users.domain.model.EventCapability;
import dev.rafex.insightbloom.users.domain.model.Ticket;
import dev.rafex.insightbloom.users.domain.model.TicketStatus;
import dev.rafex.insightbloom.users.domain.services.TicketEmailTemplate;
import dev.rafex.insightbloom.users.domain.services.TicketQrGenerator;
import dev.rafex.insightbloom.users.domain.model.User;
import dev.rafex.insightbloom.users.domain.model.ConferenceStatus;
import dev.rafex.insightbloom.users.domain.ports.ConferenceMembershipRepository;
import dev.rafex.insightbloom.users.domain.ports.ConferenceRepository;
import dev.rafex.insightbloom.users.domain.ports.EmailPort;
import dev.rafex.insightbloom.users.domain.ports.EventTypeRepository;
import dev.rafex.insightbloom.users.domain.ports.TicketRepository;
import dev.rafex.insightbloom.users.domain.ports.ReservationRepository;
import dev.rafex.insightbloom.users.domain.ports.TimezoneRepository;
import dev.rafex.insightbloom.users.domain.ports.UserRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Emisión, canje y check-in de boletos. Las transiciones sensibles son atómicas en SQLite. */
public class TicketUseCase {
    private static final long TICKET_EXPIRATION_HOURS = 5;
    /** Tope de sanidad por request -- no es una regla de negocio, evita que una request suelta
     *  intente emitir una cantidad absurda de una sola vez. */
    private static final int MAX_BATCH_ISSUE = 200;
    private static final Pattern UUID_V4 = Pattern.compile("(?i)([0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12})");
    private final ConferenceRepository conferenceRepository;
    private final EventTypeRepository eventTypeRepository;
    private final TicketRepository ticketRepository;
    private final ReservationRepository reservationRepository;
    private final ConferenceMembershipRepository membershipRepository;
    private final EmailPort emailPort;
    private final String frontendBaseUrl;
    private final TimezoneRepository timezoneRepository;
    private final UserRepository userRepository;
    private OrganizerTicketSignatureUseCase organizerTicketSignatures;

    public TicketUseCase(final ConferenceRepository conferenceRepository, final EventTypeRepository eventTypeRepository,
                         final TicketRepository ticketRepository, final ConferenceMembershipRepository membershipRepository,
                         final EmailPort emailPort, final String frontendBaseUrl,
                         final ReservationRepository reservationRepository) {
        this(conferenceRepository, eventTypeRepository, ticketRepository, membershipRepository, emailPort,
                frontendBaseUrl, reservationRepository, null, null);
    }

    public TicketUseCase(final ConferenceRepository conferenceRepository, final EventTypeRepository eventTypeRepository,
                         final TicketRepository ticketRepository, final ConferenceMembershipRepository membershipRepository,
                         final EmailPort emailPort, final String frontendBaseUrl,
                         final ReservationRepository reservationRepository, final TimezoneRepository timezoneRepository) {
        this(conferenceRepository, eventTypeRepository, ticketRepository, membershipRepository, emailPort,
                frontendBaseUrl, reservationRepository, timezoneRepository, null);
    }

    public TicketUseCase(final ConferenceRepository conferenceRepository, final EventTypeRepository eventTypeRepository,
                         final TicketRepository ticketRepository, final ConferenceMembershipRepository membershipRepository,
                         final EmailPort emailPort, final String frontendBaseUrl,
                         final ReservationRepository reservationRepository, final TimezoneRepository timezoneRepository,
                         final UserRepository userRepository) {
        this.conferenceRepository = conferenceRepository;
        this.eventTypeRepository = eventTypeRepository;
        this.ticketRepository = ticketRepository;
        this.reservationRepository = reservationRepository;
        this.membershipRepository = membershipRepository;
        this.emailPort = emailPort;
        this.frontendBaseUrl = frontendBaseUrl;
        this.timezoneRepository = timezoneRepository;
        this.userRepository = userRepository;
    }

    public void configureOrganizerTicketSignatures(final OrganizerTicketSignatureUseCase useCase) {
        this.organizerTicketSignatures = useCase;
    }

    public OrganizerTicketSignatureUseCase organizerTicketSignatures() {
        if (organizerTicketSignatures == null) throw new IllegalStateException("organizer_ticket_signing_not_configured");
        return organizerTicketSignatures;
    }

    public Ticket issue(final String conferenceUuid, final String issuerUuid, final String recipientEmail,
                        final String seatUuid) {
        return issue(conferenceUuid, issuerUuid, recipientEmail, seatUuid, 0, "LEGACY");
    }

    public Ticket issueProtected(final String conferenceUuid, final String issuerUuid, final String recipientEmail,
                                 final String seatUuid, final int maxDevices, final String enrollmentMethod) {
        if (maxDevices < 1 || maxDevices > 5) throw new IllegalArgumentException("ticket_device_limit_invalid");
        if (!"EMAIL_OTP".equals(enrollmentMethod) && !"ONE_TIME_LINK".equals(enrollmentMethod))
            throw new IllegalArgumentException("ticket_enrollment_method_invalid");
        if ("EMAIL_OTP".equals(enrollmentMethod) && (recipientEmail == null || recipientEmail.isBlank()))
            throw new IllegalArgumentException("ticket_recipient_email_required");
        return issue(conferenceUuid, issuerUuid, recipientEmail, seatUuid, maxDevices, enrollmentMethod);
    }

    private Ticket issue(final String conferenceUuid, final String issuerUuid, final String recipientEmail,
                         final String seatUuid, final int maxDevices, final String enrollmentMethod) {
        final Conference conference = conference(conferenceUuid);
        ensureConferenceActive(conference);
        final EventCapability capability = seatUuid == null ? EventCapability.TICKETING_GENERAL : EventCapability.TICKETING_SEATED;
        if (!hasCapability(conference, capability)) throw new IllegalStateException("capability_not_available");
        if ("SEATED".equals(conference.getSeatingMode()) && seatUuid == null) throw new IllegalStateException("seat_required");
        if ("SEATED".equals(conference.getSeatingMode()) == false && seatUuid != null) throw new IllegalStateException("seat_not_allowed");
        final boolean counted = conference.getCapacity() != null;
        if (counted && !conferenceRepository.tryIncrementReservedCount(conferenceUuid)) throw new IllegalStateException("capacity_exceeded");
        final Ticket ticket = new Ticket(conferenceUuid, issuerUuid, blankToNull(recipientEmail), blankToNull(seatUuid), maxDevices, enrollmentMethod);
        ticket.markPendingSignature();
        try {
            ticketRepository.insert(ticket);
            if (organizerTicketSignatures != null) organizerTicketSignatures.invalidateOfflinePackage(conferenceUuid);
        } catch (RuntimeException e) {
            if (counted) conferenceRepository.decrementReservedCount(conferenceUuid);
            throw e;
        }
        return ticket;
    }

    public Ticket finalizeTicketAfterOrganizerSignature(final String conferenceUuid, final String ticketUuid) {
        final Ticket ticket = ticketRepository.findByUuid(ticketUuid)
                .filter(t -> conferenceUuid.equals(t.getConferenceUuid()))
                .orElseThrow(() -> new IllegalArgumentException("ticket_not_found"));
        if (ticket.getStatus() != TicketStatus.PENDING_SIGNATURE) return ticket;
        if (!ticketRepository.finalizeSignature(ticketUuid)) return ticketRepository.findByUuid(ticketUuid).orElse(ticket);
        final Ticket finalized = ticketRepository.findByUuid(ticketUuid).orElseThrow();
        sendEmail(conference(conferenceUuid), finalized);
        return finalized;
    }

    /**
     * Emite varios boletos anónimos (sin destinatario, sin asiento) de una sola vez -- para
     * eventos GENERAL donde el organizador quiere pre-generar boletos que la gente reclama
     * después con el QR, en vez de emitir uno por uno. No aplica a modo SEATED (cada boleto
     * ahí requiere un asiento específico) ni cuando se quiere mandar el boleto por correo a
     * un destinatario puntual (usar {@link #issue}).
     * Valida la cantidad completa contra el aforo restante ANTES de emitir nada: o se emiten
     * los {@code quantity} boletos pedidos, o ninguno -- así no se pisa parcialmente el aforo
     * de otro emisor concurrente.
     */
    public List<Ticket> issueBatch(final String conferenceUuid, final String issuerUuid, final int quantity) {
        if (quantity < 1 || quantity > MAX_BATCH_ISSUE) throw new IllegalArgumentException("quantity_invalid");
        final Conference conference = conference(conferenceUuid);
        ensureConferenceActive(conference);
        if (!hasCapability(conference, EventCapability.TICKETING_GENERAL)) throw new IllegalStateException("capability_not_available");
        if ("SEATED".equals(conference.getSeatingMode())) throw new IllegalStateException("seat_required");
        if (conference.getCapacity() != null
                && quantity > Math.max(0, conference.getCapacity() - conference.getReservedCount())) {
            throw new IllegalStateException("capacity_exceeded");
        }
        final List<Ticket> issued = new java.util.ArrayList<>(quantity);
        try {
            for (int i = 0; i < quantity; i++) {
                issued.add(issue(conferenceUuid, issuerUuid, null, null));
            }
        } catch (final RuntimeException e) {
            for (final Ticket ticket : issued) {
                try { revoke(conferenceUuid, ticket.getUuid(), issuerUuid); } catch (final RuntimeException ignored) { }
            }
            throw e;
        }
        return issued;
    }

    public List<Ticket> issueBatchProtected(final String conferenceUuid, final String issuerUuid, final int quantity,
                                            final int maxDevices, final String enrollmentMethod) {
        if (quantity < 2 || quantity > MAX_BATCH_ISSUE) throw new IllegalArgumentException("quantity_invalid");
        if (maxDevices < 1 || maxDevices > 5) throw new IllegalArgumentException("ticket_device_limit_invalid");
        if (!"EMAIL_OTP".equals(enrollmentMethod) && !"ONE_TIME_LINK".equals(enrollmentMethod))
            throw new IllegalArgumentException("ticket_enrollment_method_invalid");
        if ("EMAIL_OTP".equals(enrollmentMethod)) throw new IllegalArgumentException("ticket_recipient_email_required");
        final Conference conference = conference(conferenceUuid);
        ensureConferenceActive(conference);
        if (!hasCapability(conference, EventCapability.TICKETING_GENERAL)) throw new IllegalStateException("capability_not_available");
        if ("SEATED".equals(conference.getSeatingMode())) throw new IllegalStateException("seat_required");
        if (conference.getCapacity() != null && quantity > Math.max(0, conference.getCapacity() - conference.getReservedCount()))
            throw new IllegalStateException("capacity_exceeded");
        final List<Ticket> issued = new java.util.ArrayList<>(quantity);
        try {
            for (int i = 0; i < quantity; i++) issued.add(issue(conferenceUuid, issuerUuid, null, null, maxDevices, enrollmentMethod));
        } catch (final RuntimeException e) {
            for (final Ticket ticket : issued) { try { revoke(conferenceUuid, ticket.getUuid(), issuerUuid); } catch (final RuntimeException ignored) { } }
            throw e;
        }
        return issued;
    }

    /**
     * Emite el boleto contado del personal operativo. Se entrega ya canjeado al usuario y queda
     * protegido contra revocación. Es idempotente para no consumir otra plaza si el rol se asigna
     * nuevamente o si se completa un evento antiguo durante el primer acceso del creador.
     */
    public Ticket issueOperational(final String conferenceUuid, final String operatorUserUuid) {
        final Conference conference = conference(conferenceUuid);
        ensureConferenceActive(conference);
        if (!isTicketed(conference)) throw new IllegalStateException("capability_not_available");
        final Optional<Ticket> existing = ticketRepository.findOperationalByConferenceAndUser(
                conferenceUuid, operatorUserUuid);
        if (existing.isPresent()) return existing.get();
        if (conference.getCapacity() == null || !conferenceRepository.tryIncrementReservedCount(conferenceUuid)) {
            throw new IllegalStateException("capacity_exceeded");
        }
        final Ticket ticket = Ticket.operational(conferenceUuid, conference.getCreatedByUserUuid(), operatorUserUuid);
        try {
            ticketRepository.insert(ticket);
        } catch (RuntimeException e) {
            conferenceRepository.decrementReservedCount(conferenceUuid);
            throw e;
        }
        return ticket;
    }

    public Ticket claim(final String conferenceUuid, final String qrOrUuid, final String userUuid) {
        final Conference conference = conference(conferenceUuid);
        if (conference.getStatus() != ConferenceStatus.ACTIVE) {
            throw new IllegalStateException("conference_closed");
        }
        expireIfNeeded(conference);
        final String code = normalizeCode(qrOrUuid);
        final Ticket ticket = ticketRepository.findByCode(conferenceUuid, code)
                .orElseThrow(() -> new IllegalArgumentException("ticket_not_found"));
        if (ticket.getStatus().name().equals("REVOKED")) throw new IllegalStateException("ticket_revoked");
        if (ticket.getStatus().name().equals("EXPIRED")) throw new IllegalStateException("ticket_expired");
        if (ticket.isDeviceProtected()) throw new IllegalStateException("ticket_device_enrollment_required");
        if (ticket.getClaimedByUserUuid() != null) {
            if (ticket.getClaimedByUserUuid().equals(userUuid)) return ticket;
            throw new IllegalStateException("ticket_already_claimed");
        }
        // La plataforma no soporta que una persona adquiera más de un boleto por evento (uso
        // por asiento/dispositivo). findByConferenceAndUser ya excluye REVOKED/EXPIRED, así que
        // un boleto revocado no bloquea reclamar uno nuevo.
        if (ticketRepository.findByConferenceAndUser(conferenceUuid, userUuid).isPresent()) {
            throw new IllegalStateException("user_already_has_ticket");
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

    /**
     * Autoservicio: reclama el primer boleto anónimo (emitido en lote, sin destinatario) que
     * siga sin reclamar, en vez de emitir uno nuevo. Así los boletos pre-emitidos que el
     * organizador NO reparte a mano (ver {@link #issueBatch}) también se pueden reclamar solos
     * desde la cartelera pública (decisión 2026-07-27: "reparto manual + autoservicio para lo
     * que sobra" -- el organizador puede seguir compartiendo el UUID/QR de algunos a mano, y
     * dejar el resto disponible acá). Reintenta contra el update atómico de la DB en
     * {@code ticketRepository.claim} por si dos personas reclaman a la vez el último boleto
     * libre: quien pierde la carrera simplemente prueba con el siguiente candidato, no falla.
     * Devuelve vacío si no queda ninguno libre (el llamador decide si emite uno nuevo).
     */
    public Optional<Ticket> claimAnyAvailable(final String conferenceUuid, final String userUuid) {
        final Conference conference = conference(conferenceUuid);
        if (conference.getStatus() != ConferenceStatus.ACTIVE) throw new IllegalStateException("conference_closed");
        expireIfNeeded(conference);
        if (ticketRepository.findByConferenceAndUser(conferenceUuid, userUuid).isPresent()) {
            throw new IllegalStateException("user_already_has_ticket");
        }
        for (final Ticket candidate : ticketRepository.findByConference(conferenceUuid)) {
            if (candidate.isOperational() || candidate.getStatus() != TicketStatus.ISSUED
                    || candidate.getClaimedByUserUuid() != null || candidate.getRecipientEmail() != null
                    || candidate.isDeviceProtected()) {
                continue;
            }
            if (ticketRepository.claim(candidate.getUuid(), userUuid, Instant.now().toString())) {
                if (!membershipRepository.exists(userUuid, conferenceUuid)) {
                    membershipRepository.recordJoin(new ConferenceMembership(userUuid, conferenceUuid,
                            conference.getName(), conference.getFriendlyId()));
                }
                return ticketRepository.findByUuid(candidate.getUuid());
            }
        }
        return Optional.empty();
    }

    /** Canjea un boleto cuando el usuario sólo dispone del contenido del QR/UUID. */
    public Ticket claimByCode(final String qrOrUuid, final String userUuid) {
        final String code = normalizeCode(qrOrUuid);
        final Ticket ticket = ticketRepository.findByTicketCode(code)
                .or(() -> ticketRepository.findByUuid(code))
                .orElseThrow(() -> new IllegalArgumentException("ticket_not_found"));
        return claim(ticket.getConferenceUuid(), ticket.getTicketCode(), userUuid);
    }

    public Optional<Ticket> myTicket(final String conferenceUuid, final String userUuid) {
        final Conference conference = conference(conferenceUuid);
        expireIfNeeded(conference);
        return ticketRepository.findByConferenceAndUser(conferenceUuid, userUuid);
    }

    public Optional<Ticket> pendingProtectedTicketForEmail(final String conferenceUuid, final String email) {
        if (email == null || email.isBlank()) return Optional.empty();
        return ticketRepository.findByConference(conferenceUuid).stream()
                .filter(t -> t.isDeviceProtected() && t.getStatus() == TicketStatus.ISSUED
                        && t.getClaimedByUserUuid() == null && email.equalsIgnoreCase(t.getRecipientEmail()))
                .findFirst();
    }

    public List<Ticket> list(final String conferenceUuid) {
        final Conference conference = conference(conferenceUuid);
        expireIfNeeded(conference);
        return ticketRepository.findByConference(conferenceUuid);
    }

    public Optional<Ticket> ticketByUuid(final String conferenceUuid, final String ticketUuid) {
        return ticketRepository.findByUuid(ticketUuid).filter(t -> conferenceUuid.equals(t.getConferenceUuid()));
    }

    public Ticket completeBoundClaim(final String conferenceUuid, final String ticketUuid, final String userUuid) {
        final Conference conference = conference(conferenceUuid);
        final Ticket ticket = ticketByUuid(conferenceUuid, ticketUuid)
                .filter(t -> userUuid.equals(t.getClaimedByUserUuid()))
                .orElseThrow(() -> new IllegalArgumentException("ticket_not_found"));
        if (!membershipRepository.exists(userUuid, conferenceUuid)) {
            membershipRepository.recordJoin(new ConferenceMembership(userUuid, conferenceUuid, conference.getName(), conference.getFriendlyId()));
        }
        return ticket;
    }

    /**
     * Datos de gestión protegidos para el dashboard. Mantiene el ticket completo para no romper
     * QR/copia/revocación y agrega únicamente métricas de aforo y perfiles mínimos de los
     * usuarios que participan en la auditoría del boleto. Nunca expone credenciales ni datos
     * sensibles del usuario.
     */
    public TicketManagementSummary listManagement(final String conferenceUuid) {
        final Conference conference = conference(conferenceUuid);
        expireIfNeeded(conference);
        final List<Ticket> tickets = ticketRepository.findByConference(conferenceUuid);
        final Map<String, UserSummary> claimedUsers = new LinkedHashMap<>();
        final Map<String, UserSummary> ticketActors = new LinkedHashMap<>();
        if (userRepository != null) {
            tickets.stream()
                    .map(Ticket::getClaimedByUserUuid)
                    .filter(uuid -> uuid != null && !claimedUsers.containsKey(uuid))
                    .forEach(uuid -> userRepository.findByUuid(uuid).ifPresent(user ->
                            claimedUsers.put(uuid, UserSummary.from(user))));
            tickets.stream()
                    .flatMap(ticket -> java.util.stream.Stream.of(
                            ticket.getClaimedByUserUuid(), ticket.getRevokedByUserUuid()))
                    .filter(uuid -> uuid != null && !ticketActors.containsKey(uuid))
                    .forEach(uuid -> userRepository.findByUuid(uuid).ifPresent(user ->
                            ticketActors.put(uuid, UserSummary.from(user))));
        }
        final Integer remaining = conference.getCapacity() == null
                ? null
                : Math.max(0, conference.getCapacity() - conference.getReservedCount());
        return new TicketManagementSummary(conference.getCapacity(), conference.getReservedCount(),
                remaining, tickets, claimedUsers, ticketActors);
    }

    public record UserSummary(String uuid, String displayName, String username, String email) {
        static UserSummary from(final User user) {
            return new UserSummary(user.getUuid(), user.getDisplayName(), user.getUsername(), user.getEmail());
        }
    }

    public record TicketManagementSummary(Integer capacity, int reservedCount, Integer remainingToIssue,
                                           List<Ticket> tickets, Map<String, UserSummary> claimedUsers,
                                           Map<String, UserSummary> ticketActors) {}

    public Ticket checkIn(final String conferenceUuid, final String qrOrUuid) {
        return checkIn(conferenceUuid, qrOrUuid, false);
    }

    public Ticket checkInVerifiedDevicePresentation(final String conferenceUuid, final String qrOrUuid) {
        return checkIn(conferenceUuid, qrOrUuid, true);
    }

    private Ticket checkIn(final String conferenceUuid, final String qrOrUuid, final boolean deviceVerified) {
        final Conference conference = conference(conferenceUuid);
        expireIfNeeded(conference);
        final String code = normalizeCode(qrOrUuid);
        final Ticket ticket = ticketRepository.findByCode(conferenceUuid, code)
                .or(() -> ticketRepository.findByUuid(code))
                .orElseThrow(() -> new IllegalArgumentException("ticket_not_found"));
        if (!conferenceUuid.equals(ticket.getConferenceUuid())) throw new IllegalArgumentException("ticket_not_found");
        if (ticket.isDeviceProtected() && !deviceVerified) throw new IllegalStateException("ticket_device_proof_required");
        if (ticket.getStatus().name().equals("CHECKED_IN")) throw new IllegalStateException("already_checked_in");
        if (ticket.getStatus().name().equals("EXPIRED")) throw new IllegalStateException("ticket_expired");
        if (ticket.getStatus().name().equals("ISSUED")) throw new IllegalStateException("ticket_not_claimed");
        if (!ticketRepository.checkIn(ticket.getUuid(), Instant.now().toString())) throw new IllegalStateException("already_checked_in");
        if (organizerTicketSignatures != null) organizerTicketSignatures.invalidateOfflinePackage(conferenceUuid);
        return ticketRepository.findByUuid(ticket.getUuid()).orElseThrow(() -> new IllegalArgumentException("ticket_not_found"));
    }

    public Ticket revoke(final String conferenceUuid, final String ticketUuid, final String revokedByUserUuid) {
        final Conference conference = conference(conferenceUuid);
        expireIfNeeded(conference);
        final Ticket ticket = ticketRepository.findByUuid(ticketUuid)
                .filter(t -> conferenceUuid.equals(t.getConferenceUuid()))
                .orElseThrow(() -> new IllegalArgumentException("ticket_not_found"));
        if (ticket.isOperational()) throw new IllegalStateException("operational_ticket_protected");
        if (!ticketRepository.revoke(ticketUuid, revokedByUserUuid, Instant.now().toString())) {
            throw new IllegalStateException("ticket_already_used");
        }
        if (organizerTicketSignatures != null) organizerTicketSignatures.invalidateOfflinePackage(conferenceUuid);
        if (conference.getCapacity() != null) {
            conferenceRepository.decrementReservedCount(conferenceUuid);
        }
        return ticketRepository.findByUuid(ticketUuid).orElse(ticket);
    }

    public boolean hasAccess(final Conference conference, final String userUuid) {
        if (conference.getStatus() != ConferenceStatus.ACTIVE) return false;
        if (!isTicketed(conference)) return true;
        if (conference.getExpiresAt() != null && conference.getExpiresAt().isBefore(Instant.now())) {
            expireIfNeeded(conference);
            return false;
        }
        expireIfNeeded(conference);
        if (conference.getCreatedByUserUuid() != null
                && conference.getCreatedByUserUuid().equals(userUuid)
                && ticketRepository.findOperationalByConferenceAndUser(conference.getUuid(), userUuid).isEmpty()) {
            // Compatibilidad para eventos creados antes de los boletos operativos.
            issueOperational(conference.getUuid(), userUuid);
        }
        // En un evento ticketed, la reserva sólo aparta aforo; el acceso efectivo depende de un
        // boleto CLAIMED/CHECKED_IN vigente. Mantener la reserva como alternativa permitía que un
        // usuario conservara acceso después de que el moderador revocara su boleto.
        return ticketRepository.findByConferenceAndUser(conference.getUuid(), userUuid).isPresent();
    }

    public boolean isTicketed(final Conference conference) {
        return hasCapability(conference, EventCapability.TICKETING_GENERAL) || hasCapability(conference, EventCapability.TICKETING_SEATED);
    }

    /**
     * Indica si el autoservicio público ya no puede adquirir un boleto nuevo. Los boletos
     * sin destinatario no se consumen automáticamente: requieren que el organizador comparta
     * su enlace de activación de un solo uso.
     */
    public boolean isPublicTicketSoldOut(final Conference conference) {
        if (!isTicketed(conference) || conference.getCapacity() == null) return false;
        return conference.getReservedCount() >= conference.getCapacity();
    }

    /**
     * Asientos realmente ocupados para la vista pública de aforo -- NO es lo mismo que
     * {@code Conference.reservedCount}: ese contador sube apenas se EMITE un boleto (incluidos
     * los lotes de {@link #issueBatch} que nadie reclamó todavía) porque sirve para bloquear
     * atómicamente el sobre-cupo al emitir/reservar. Acá contamos boletos CLAIMED/CHECKED_IN u
     * operativos (personal del evento, que siempre cuenta) -- boletos ISSUED sin reclamar no
     * ocupan un lugar real todavía. Reportado 2026-07-27: un organizador pre-emitía 15 boletos
     * sin reclamar y la página pública mostraba "0 disponibles de 15" sin que nadie hubiese
     * confirmado asistencia.
     */
    public int countOccupiedSeats(final String conferenceUuid) {
        int occupied = 0;
        for (final Ticket ticket : ticketRepository.findByConference(conferenceUuid)) {
            if (ticket.isOperational() || ticket.getStatus() == TicketStatus.CLAIMED
                    || ticket.getStatus() == TicketStatus.CHECKED_IN) {
                occupied++;
            }
        }
        return occupied;
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
        if (ticket.isDeviceProtected()) {
            if ("EMAIL_OTP".equals(ticket.getDeviceEnrollmentMethod())) {
                sendProtectedSetupEmail(conference, ticket);
            }
            return;
        }
        sendTicketEmail(conference, ticket, ticket.getRecipientEmail());
    }

    private void sendProtectedSetupEmail(final Conference conference, final Ticket ticket) {
        if (!emailPort.isEnabled() || ticket.getRecipientEmail() == null) return;
        try {
            final String setupUrl = frontendBaseUrl.replaceAll("/$", "") + "/ticket-enroll?event="
                    + java.net.URLEncoder.encode(conference.getUuid(), java.nio.charset.StandardCharsets.UTF_8)
                    + "&ticketCode=" + java.net.URLEncoder.encode(ticket.getTicketCode(), java.nio.charset.StandardCharsets.UTF_8);
            final String html = "<p>Completa la activación de tu boleto para " + escapeHtml(conference.getName())
                    + ". Abre el enlace y valida el código que enviaremos a este correo.</p><p><a href=\""
                    + setupUrl + "\">Activar mi boleto</a></p>";
            emailPort.sendHtml(ticket.getRecipientEmail(), "Activa tu boleto para " + conference.getName(), html);
        } catch (final Exception ignored) { }
    }

    /**
     * Reenvía manualmente un boleto ya emitido (operativa normal: el asistente perdió el
     * correo, lo pide de nuevo, etc.). A diferencia de {@link #sendEmail}, que sólo dispara
     * automáticamente cuando se emitió con un destinatario explícito, el reenvío también
     * resuelve el correo de la cuenta que reclamó el boleto (autoservicio/cartelera), así el
     * organizador puede reenviar cualquier boleto reclamado aunque no se haya emitido con
     * `recipientEmail`.
     */
    public Ticket resend(final String conferenceUuid, final String ticketUuid) {
        final Conference conference = conference(conferenceUuid);
        final Ticket ticket = ticketRepository.findByUuid(ticketUuid)
                .filter(t -> conferenceUuid.equals(t.getConferenceUuid()))
                .orElseThrow(() -> new IllegalArgumentException("ticket_not_found"));
        if (ticket.getStatus() == TicketStatus.REVOKED) throw new IllegalStateException("ticket_revoked");
        if (ticket.getStatus() == TicketStatus.EXPIRED) throw new IllegalStateException("ticket_expired");
        if (ticket.getStatus() == TicketStatus.PENDING_SIGNATURE) throw new IllegalStateException("ticket_signature_pending");
        if (!emailPort.isEnabled()) throw new IllegalStateException("email_provider_not_configured");
        final String email = resolveEmail(ticket);
        if (email == null) throw new IllegalStateException("no_email_available");
        if (ticket.isDeviceProtected()) {
            if ("EMAIL_OTP".equals(ticket.getDeviceEnrollmentMethod())) sendProtectedSetupEmail(conference, ticket);
            else throw new IllegalStateException("ticket_enrollment_link_must_be_resent");
            return ticket;
        }
        sendTicketEmail(conference, ticket, email);
        return ticket;
    }

    /** Reenvía en lote todos los boletos con un correo resoluble; salta revocados/expirados/sin correo. */
    public ResendSummary resendAll(final String conferenceUuid) {
        final Conference conference = conference(conferenceUuid);
        int sent = 0;
        int skipped = 0;
        for (final Ticket ticket : ticketRepository.findByConference(conferenceUuid)) {
            if (ticket.getStatus() == TicketStatus.REVOKED || ticket.getStatus() == TicketStatus.EXPIRED
                    || ticket.getStatus() == TicketStatus.PENDING_SIGNATURE) {
                skipped++;
                continue;
            }
            final String email = resolveEmail(ticket);
            if (email == null || !emailPort.isEnabled()) {
                skipped++;
                continue;
            }
            sendTicketEmail(conference, ticket, email);
            sent++;
        }
        return new ResendSummary(sent, skipped);
    }

    public record ResendSummary(int sent, int skipped) {}

    /** Correo destino de un boleto: el explícito de emisión, o si no hay, el de la cuenta que lo reclamó. */
    private String resolveEmail(final Ticket ticket) {
        if (ticket.getRecipientEmail() != null) return ticket.getRecipientEmail();
        if (ticket.getClaimedByUserUuid() != null && userRepository != null) {
            return userRepository.findByUuid(ticket.getClaimedByUserUuid()).map(User::getEmail).orElse(null);
        }
        return null;
    }

    private void sendTicketEmail(final Conference conference, final Ticket ticket, final String email) {
        if (!emailPort.isEnabled() || email == null) return;
        try {
            final String ticketUrl = frontendBaseUrl + "/c/" + conference.getFriendlyId()
                    + "/ticket?ticket=" + ticket.getTicketCode();
            final byte[] qrPng = TicketQrGenerator.toPngBytes(ticketUrl);
            final String html = TicketEmailTemplate.render(conference.getName(), ticketUrl, "cid:ticket-qr", ticket.getTicketCode());
            emailPort.sendHtml(email, "Tu boleto para " + conference.getName(), html,
                    List.of(new EmailPort.EmailAttachment("ticket-qr.png", "image/png", qrPng, "ticket-qr")));
        } catch (Exception ignored) { }
    }

    private static String blankToNull(final String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static String escapeHtml(final String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
