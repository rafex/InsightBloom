package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.Conference;
import dev.rafex.insightbloom.users.domain.model.Reservation;
import dev.rafex.insightbloom.users.domain.model.User;
import dev.rafex.insightbloom.users.domain.ports.ConferenceRepository;
import dev.rafex.insightbloom.users.domain.ports.EmailPort;
import dev.rafex.insightbloom.users.domain.ports.ReservationRepository;
import dev.rafex.insightbloom.users.domain.ports.UserRepository;
import dev.rafex.insightbloom.users.domain.ports.VenueSeatRepository;

/** Reserva de un asiento específico para conferencias en modo SEATED. */
public class ReserveSeatUseCase {
    private final ConferenceRepository conferenceRepository;
    private final ReservationRepository reservationRepository;
    private final VenueSeatRepository venueSeatRepository;
    private final UserRepository userRepository;
    private final EmailPort emailPort;
    private final String frontendBaseUrl;

    public ReserveSeatUseCase(final ConferenceRepository conferenceRepository,
                               final ReservationRepository reservationRepository,
                               final VenueSeatRepository venueSeatRepository,
                               final UserRepository userRepository,
                               final EmailPort emailPort,
                               final String frontendBaseUrl) {
        this.conferenceRepository = conferenceRepository;
        this.reservationRepository = reservationRepository;
        this.venueSeatRepository = venueSeatRepository;
        this.userRepository = userRepository;
        this.emailPort = emailPort;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    public Reservation execute(final String conferenceUuid, final String userUuid, final String seatUuid) {
        final Conference conference = conferenceRepository.findByUuid(conferenceUuid)
                .orElseThrow(() -> new IllegalArgumentException("conference_not_found"));
        if (!"SEATED".equals(conference.getSeatingMode())) {
            throw new IllegalStateException("not_seated_admission");
        }
        // Admin/organizer/moderator no consumen boleto/aforo -- ver ReserveGeneralUseCase para
        // el mismo chequeo y el porqué (User.isExemptFromTickets()).
        final boolean staffExempt = userRepository.findByUuid(userUuid)
                .map(User::isExemptFromTickets).orElse(false);
        if (staffExempt) {
            throw new IllegalStateException("staff_exempt_no_ticket_needed");
        }
        venueSeatRepository.findByConference(conferenceUuid).stream()
                .filter(s -> s.getUuid().equals(seatUuid))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("seat_not_found"));

        final var existing = reservationRepository.findByConferenceAndUser(conferenceUuid, userUuid);
        if (existing.isPresent()) return existing.get();

        // El aforo aplica también en modo SEATED (además de estar acotado por la cantidad de
        // asientos definidos) -- la infraestructura tiene recursos finitos (DEC 2026-07-18).
        if (!conferenceRepository.tryIncrementReservedCount(conferenceUuid)) {
            throw new IllegalStateException("capacity_exceeded");
        }

        final Reservation reservation = new Reservation(conferenceUuid, userUuid, seatUuid);
        try {
            reservationRepository.insertNew(reservation);
        } catch (final RuntimeException e) {
            conferenceRepository.decrementReservedCount(conferenceUuid);
            final String message = e.getMessage() != null ? e.getMessage() : "";
            if (message.contains("UNIQUE constraint failed")) {
                throw new IllegalStateException("seat_already_taken");
            }
            throw e;
        }
        sendConfirmationEmail(userUuid, conference, reservation);
        return reservation;
    }

    /** Best-effort: un fallo de correo nunca debe interrumpir la reserva del asiento. */
    private void sendConfirmationEmail(final String userUuid, final Conference conference, final Reservation reservation) {
        try {
            if (!emailPort.isEnabled()) return;
            final User user = userRepository.findByUuid(userUuid).orElse(null);
            if (user == null || user.getEmail() == null || user.getEmail().isBlank()) return;
            final String subject = "Tu asiento en " + conference.getName();
            final String body = """
                    ¡Hola!

                    Confirmamos tu asiento en "%s".

                    Tu boleto: %s/c/%s/ticket

                    Nos vemos ahí.
                    """.formatted(conference.getName(), frontendBaseUrl, conference.getFriendlyId());
            emailPort.send(user.getEmail(), subject, body);
        } catch (final Exception e) {
            // best-effort
        }
    }
}
