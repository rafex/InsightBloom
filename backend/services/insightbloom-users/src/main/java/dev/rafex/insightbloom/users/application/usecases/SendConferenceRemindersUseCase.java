package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.Conference;
import dev.rafex.insightbloom.users.domain.model.Timezone;
import dev.rafex.insightbloom.users.domain.model.User;
import dev.rafex.insightbloom.users.domain.ports.ConferenceMembershipRepository;
import dev.rafex.insightbloom.users.domain.ports.ConferenceRepository;
import dev.rafex.insightbloom.users.domain.ports.EmailPort;
import dev.rafex.insightbloom.users.domain.ports.ReservationRepository;
import dev.rafex.insightbloom.users.domain.ports.TimezoneRepository;
import dev.rafex.insightbloom.users.domain.ports.UserRepository;
import dev.rafex.insightbloom.users.domain.services.IcsCalendarBuilder;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Ticked periodically (see {@code UsersApplication}'s scheduler) to email registered attendees
 * ~1h and ~1 day before a conference starts. Each reminder is sent at most once, tracked via
 * {@code reminder_sent_at} and {@code day_before_reminder_sent_at} — cleared automatically
 * if the organizer reschedules the event (see {@code UpdateConferenceUseCase}).
 * Recipients are determined by Reservation records (authoritative enrollment source as of 2026-07-18).
 */
public class SendConferenceRemindersUseCase {

    private static final long WINDOW_1H_START_MINUTES = 55;
    private static final long WINDOW_1H_END_MINUTES = 65;

    private static final long WINDOW_1DAY_START_HOURS = 23;
    private static final long WINDOW_1DAY_END_HOURS = 25;

    private final ConferenceRepository conferenceRepository;
    private final UserRepository userRepository;
    private final TimezoneRepository timezoneRepository;
    private final EmailPort emailPort;
    private final ReservationRepository reservationRepository;
    private final SendNotificationUseCase sendNotificationUseCase;
    private final String frontendBaseUrl;

    public SendConferenceRemindersUseCase(final ConferenceRepository conferenceRepository,
                                           final UserRepository userRepository,
                                           final TimezoneRepository timezoneRepository,
                                           final EmailPort emailPort,
                                           final ReservationRepository reservationRepository,
                                           final String frontendBaseUrl) {
        this(conferenceRepository, userRepository, timezoneRepository, emailPort, reservationRepository, frontendBaseUrl, null);
    }

    public SendConferenceRemindersUseCase(final ConferenceRepository conferenceRepository,
                                           final UserRepository userRepository,
                                           final TimezoneRepository timezoneRepository,
                                           final EmailPort emailPort,
                                           final ReservationRepository reservationRepository,
                                           final String frontendBaseUrl,
                                           final SendNotificationUseCase sendNotificationUseCase) {
        this.conferenceRepository = conferenceRepository;
        this.userRepository = userRepository;
        this.timezoneRepository = timezoneRepository;
        this.emailPort = emailPort;
        this.reservationRepository = reservationRepository;
        this.frontendBaseUrl = frontendBaseUrl;
        this.sendNotificationUseCase = sendNotificationUseCase;
    }

    public void execute(final Instant now) {
        if (!emailPort.isEnabled()) return;
        send1HourReminders(now);
        send1DayBeforeReminders(now);
    }

    private void send1HourReminders(final Instant now) {
        for (final Conference conference : conferenceRepository.findPendingReminder()) {
            final Instant startInstant = resolveStartInstant(conference);
            if (startInstant == null) continue;
            final long minutesUntilStart = java.time.Duration.between(now, startInstant).toMinutes();
            if (minutesUntilStart < WINDOW_1H_START_MINUTES || minutesUntilStart > WINDOW_1H_END_MINUTES) continue;

            sendRemindersFor(conference, "1 hora");
            conference.setReminderSentAt(now);
            conferenceRepository.save(conference);
        }
    }

    private void send1DayBeforeReminders(final Instant now) {
        for (final Conference conference : conferenceRepository.findPendingDayBeforeReminder()) {
            final Instant startInstant = resolveStartInstant(conference);
            if (startInstant == null) continue;
            final long hoursUntilStart = java.time.Duration.between(now, startInstant).toHours();
            if (hoursUntilStart < WINDOW_1DAY_START_HOURS || hoursUntilStart > WINDOW_1DAY_END_HOURS) continue;

            sendRemindersFor(conference, "1 día");
            conference.setDayBeforeReminderSentAt(now);
            conferenceRepository.save(conference);
        }
    }

    private void sendRemindersFor(final Conference conference, final String timeframe) {
        final List<EmailPort.EmailAttachment> attachments = buildCalendarAttachment(conference);
        for (final var reservation : reservationRepository.findByConference(conference.getUuid())) {
            try {
                final User user = userRepository.findByUuid(reservation.getUserUuid()).orElse(null);
                if (user == null || user.getEmail() == null || user.getEmail().isBlank()) continue;
                final String subject = "Tu conferencia " + conference.getName() + " empieza en " + timeframe;
                final String ticketLine = ticketReminderLine(conference, reservation.getUserUuid());
                final String htmlBody = """
                        <p>¡Hola!</p>
                        <p>Recordatorio: "%s" empieza en aproximadamente %s.</p>
                        %s%s
                        <p>Nos vemos ahí.</p>
                        """.formatted(conference.getName(), timeframe,
                        conference.getVenue() != null && !conference.getVenue().isBlank()
                                ? "<p>Sede: " + conference.getVenue() + "</p>" : "",
                        ticketLine.isBlank() ? "" : "<p>" + ticketLine + "</p>");
                emailPort.sendHtml(user.getEmail(), subject, htmlBody, attachments);
            } catch (final Exception e) {
                // best-effort: un fallo por asistente no debe frenar al resto
            }
            sendReminderNotification(reservation.getUserUuid(), conference, timeframe);
        }
    }

    private List<EmailPort.EmailAttachment> buildCalendarAttachment(final Conference conference) {
        try {
            if (conference.getEventDate() == null || conference.getEventDate().isBlank()
                    || conference.getStartTime() == null || conference.getStartTime().isBlank()) {
                return List.of();
            }
            final Integer tzId = conference.getTimezoneId();
            final Integer offsetMinutes = tzId != null
                    ? timezoneRepository.findById(tzId).map(Timezone::utcOffsetMinutes).orElse(-360)
                    : -360;
            final var icsInput = new IcsCalendarBuilder.CalendarEventInput(
                    conference.getName(),
                    conference.getEventDate(),
                    conference.getStartTime(),
                    conference.getEndTime(),
                    conference.getVenue(),
                    offsetMinutes
            );
            final String icsContent = IcsCalendarBuilder.buildIcs(icsInput);
            final byte[] icsBytes = icsContent.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            return List.of(new EmailPort.EmailAttachment("evento.ics", "text/calendar", icsBytes, null));
        } catch (final Exception e) {
            // best-effort: si no se puede generar el .ics, el email se envía sin adjunto
            return List.of();
        }
    }

    private void sendReminderNotification(final String userUuid, final Conference conference, final String timeframe) {
        try {
            if (sendNotificationUseCase == null) return;
            sendNotificationUseCase.execute(userUuid, "conference_reminder_" + timeframe.replace(" ", ""),
                    "Recordatorio: " + conference.getName(),
                    "Tu conferencia empieza en aproximadamente " + timeframe + ".",
                    frontendBaseUrl + "/c/" + conference.getFriendlyId());
        } catch (final Exception e) {
            // best-effort
        }
    }

    /** Agrega el link al boleto cuando la conferencia usa reservas; nudge a elegir asiento si aún falta. */
    private String ticketReminderLine(final Conference conference, final String userUuid) {
        if ("NONE".equals(conference.getSeatingMode())) return "";
        final var reservation = reservationRepository.findByConferenceAndUser(conference.getUuid(), userUuid);
        final String ticketUrl = frontendBaseUrl + "/c/" + conference.getFriendlyId() + "/ticket";
        if (reservation.isPresent()) {
            return "\nTu boleto: " + ticketUrl + "\n";
        }
        return "\nAún no has elegido tu asiento: " + ticketUrl + "\n";
    }

    private Instant resolveStartInstant(final Conference conference) {
        try {
            final LocalDate date = LocalDate.parse(conference.getEventDate());
            final LocalTime time = LocalTime.parse(conference.getStartTime());
            final int offsetMinutes = resolveOffsetMinutes(conference);
            return date.atTime(time).toInstant(java.time.ZoneOffset.ofTotalSeconds(offsetMinutes * 60));
        } catch (final Exception e) {
            return null;
        }
    }

    private int resolveOffsetMinutes(final Conference conference) {
        if (conference.getTimezoneId() == null) return -360; // GMT-6 por defecto
        return timezoneRepository.findById(conference.getTimezoneId())
                .map(Timezone::utcOffsetMinutes)
                .orElse(-360);
    }
}
