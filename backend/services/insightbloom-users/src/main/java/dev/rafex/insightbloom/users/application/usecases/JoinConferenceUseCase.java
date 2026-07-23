package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.Conference;
import dev.rafex.insightbloom.users.domain.model.ConferenceMembership;
import dev.rafex.insightbloom.users.domain.model.Reservation;
import dev.rafex.insightbloom.users.domain.model.User;
import dev.rafex.insightbloom.users.domain.ports.ConferenceMembershipRepository;
import dev.rafex.insightbloom.users.domain.ports.EmailPort;
import dev.rafex.insightbloom.users.domain.ports.TimezoneRepository;
import dev.rafex.insightbloom.users.domain.ports.UserRepository;
import dev.rafex.insightbloom.users.domain.model.Permission;
import dev.rafex.insightbloom.users.domain.model.ConferenceStatus;
import dev.rafex.insightbloom.users.domain.services.EventPermissionGuard;

public class JoinConferenceUseCase {
    private final GetConferenceUseCase getConferenceUseCase;
    private final ConferenceMembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final EmailPort emailPort;
    private final TimezoneRepository timezoneRepository;
    private final ReserveGeneralUseCase reserveGeneralUseCase;
    private final TicketUseCase ticketUseCase;
    private final EventPermissionGuard eventPermissionGuard;
    private final String frontendBaseUrl;

    public JoinConferenceUseCase(final GetConferenceUseCase getConferenceUseCase,
                                  final ConferenceMembershipRepository membershipRepository,
                                  final UserRepository userRepository,
                                  final EmailPort emailPort,
                                  final TimezoneRepository timezoneRepository,
                                  final ReserveGeneralUseCase reserveGeneralUseCase,
                                  final String frontendBaseUrl) {
        this(getConferenceUseCase, membershipRepository, userRepository, emailPort, timezoneRepository,
                reserveGeneralUseCase, frontendBaseUrl, null, null);
    }

    public JoinConferenceUseCase(final GetConferenceUseCase getConferenceUseCase,
                                  final ConferenceMembershipRepository membershipRepository,
                                  final UserRepository userRepository,
                                  final EmailPort emailPort,
                                  final TimezoneRepository timezoneRepository,
                                  final ReserveGeneralUseCase reserveGeneralUseCase,
                                  final String frontendBaseUrl,
                                  final TicketUseCase ticketUseCase,
                                  final EventPermissionGuard eventPermissionGuard) {
        this.getConferenceUseCase = getConferenceUseCase;
        this.membershipRepository = membershipRepository;
        this.userRepository = userRepository;
        this.emailPort = emailPort;
        this.timezoneRepository = timezoneRepository;
        this.reserveGeneralUseCase = reserveGeneralUseCase;
        this.frontendBaseUrl = frontendBaseUrl;
        this.ticketUseCase = ticketUseCase;
        this.eventPermissionGuard = eventPermissionGuard;
    }

    public Conference execute(final String userUuid, final String identifier) {
        final Conference conference = getConferenceUseCase.resolveAny(identifier)
                .orElseThrow(() -> new IllegalArgumentException("conference_not_found"));
        if (conference.getStatus() != ConferenceStatus.ACTIVE) {
            throw new IllegalStateException("conference_closed");
        }
        final boolean ticketed = ticketUseCase != null && ticketUseCase.isTicketed(conference);
        final boolean staffOrManager = userRepository.findByUuid(userUuid)
                .map(User::isExemptFromTickets).orElse(false)
                || (eventPermissionGuard != null && eventPermissionGuard.hasPermission(
                conference.getUuid(), userUuid, null, Permission.MANAGE_TICKETS));
        if (ticketed && !staffOrManager && !ticketUseCase.hasAccess(conference, userUuid)) {
            throw new IllegalStateException("ticket_required");
        }
        final boolean firstJoin = !membershipRepository.exists(userUuid, conference.getUuid());
        membershipRepository.recordJoin(new ConferenceMembership(
                userUuid, conference.getUuid(), conference.getName(), conference.getFriendlyId()));

        Reservation reservation = null;
        // Modo SEATED no auto-reserva: el asistente debe elegir un asiento explícitamente.
        // GENERAL y NONE emiten boleto sin asiento, sujeto al aforo del evento (el aforo ya no es
        // exclusivo de GENERAL -- el servidor tiene recursos finitos y todo evento lo declara).
        if (ticketUseCase == null && firstJoin && !"SEATED".equals(conference.getSeatingMode())) {
            try {
                reservation = reserveGeneralUseCase.execute(conference.getUuid(), userUuid);
            } catch (final Exception e) {
                // si el aforo ya se llenó justo al unirse, el join igual procede sin boleto;
                // el asistente verá el estado real al abrir "Mi boleto".
            }
        }
        if (firstJoin) {
            sendConfirmationEmail(userUuid, conference, reservation);
        }
        return conference;
    }

    /** Best-effort: un fallo de correo nunca debe interrumpir el registro a la conferencia. */
    private void sendConfirmationEmail(final String userUuid, final Conference conference, final Reservation reservation) {
        try {
            if (!emailPort.isEnabled()) return;
            final User user = userRepository.findByUuid(userUuid).orElse(null);
            if (user == null || user.getEmail() == null || user.getEmail().isBlank()) return;

            final String schedule = describeSchedule(conference);
            final String subject = "Te has apuntado a la conferencia " + conference.getName();
            final String ticketLine = reservation != null
                    ? "\nTu boleto: " + frontendBaseUrl + "/c/" + conference.getFriendlyId() + "/ticket\n"
                    : "";
            final String body = """
                    ¡Hola!

                    Confirmamos tu registro en "%s".
                    %s%s%s
                    Nos vemos ahí.
                    """.formatted(
                    conference.getName(),
                    schedule.isBlank() ? "" : "\n" + schedule + "\n",
                    conference.getVenue() != null && !conference.getVenue().isBlank()
                            ? "\nSede: " + conference.getVenue() + "\n" : "",
                    ticketLine);
            emailPort.send(user.getEmail(), subject, body);
        } catch (final Exception e) {
            // best-effort
        }
    }

    private String describeSchedule(final Conference conference) {
        if (conference.getEventDate() == null || conference.getEventDate().isBlank()) return "";
        final String tzLabel = conference.getTimezoneId() != null
                ? timezoneRepository.findById(conference.getTimezoneId()).map(t -> " (" + t.label() + ")").orElse("")
                : "";
        final String time = conference.getStartTime() != null && !conference.getStartTime().isBlank()
                ? " a las " + conference.getStartTime() : "";
        return "Fecha: " + conference.getEventDate() + time + tzLabel;
    }
}
