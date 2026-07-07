package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.Conference;
import dev.rafex.insightbloom.users.domain.model.Timezone;
import dev.rafex.insightbloom.users.domain.model.User;
import dev.rafex.insightbloom.users.domain.ports.ConferenceMembershipRepository;
import dev.rafex.insightbloom.users.domain.ports.ConferenceRepository;
import dev.rafex.insightbloom.users.domain.ports.EmailPort;
import dev.rafex.insightbloom.users.domain.ports.TimezoneRepository;
import dev.rafex.insightbloom.users.domain.ports.UserRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Ticked periodically (see {@code UsersApplication}'s scheduler) to email registered attendees
 * ~1h before a conference starts. Each conference is reminded at most once, tracked via
 * {@code reminder_sent_at} — cleared automatically if the organizer reschedules the event
 * (see {@code UpdateConferenceUseCase}).
 */
public class SendConferenceRemindersUseCase {

    private static final long WINDOW_START_MINUTES = 55;
    private static final long WINDOW_END_MINUTES = 65;

    private final ConferenceRepository conferenceRepository;
    private final ConferenceMembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final TimezoneRepository timezoneRepository;
    private final EmailPort emailPort;

    public SendConferenceRemindersUseCase(final ConferenceRepository conferenceRepository,
                                           final ConferenceMembershipRepository membershipRepository,
                                           final UserRepository userRepository,
                                           final TimezoneRepository timezoneRepository,
                                           final EmailPort emailPort) {
        this.conferenceRepository = conferenceRepository;
        this.membershipRepository = membershipRepository;
        this.userRepository = userRepository;
        this.timezoneRepository = timezoneRepository;
        this.emailPort = emailPort;
    }

    public void execute(final Instant now) {
        if (!emailPort.isEnabled()) return;
        for (final Conference conference : conferenceRepository.findPendingReminder()) {
            final Instant startInstant = resolveStartInstant(conference);
            if (startInstant == null) continue;
            final long minutesUntilStart = java.time.Duration.between(now, startInstant).toMinutes();
            if (minutesUntilStart < WINDOW_START_MINUTES || minutesUntilStart > WINDOW_END_MINUTES) continue;

            sendRemindersFor(conference);
            conference.setReminderSentAt(now);
            conferenceRepository.save(conference);
        }
    }

    private void sendRemindersFor(final Conference conference) {
        for (final var membership : membershipRepository.findByConference(conference.getUuid())) {
            try {
                final User user = userRepository.findByUuid(membership.getUserUuid()).orElse(null);
                if (user == null || user.getEmail() == null || user.getEmail().isBlank()) continue;
                final String subject = "Tu conferencia " + conference.getName() + " empieza en 1 hora";
                final String body = """
                        ¡Hola!

                        Recordatorio: "%s" empieza en aproximadamente 1 hora.
                        %s
                        Nos vemos ahí.
                        """.formatted(conference.getName(),
                        conference.getVenue() != null && !conference.getVenue().isBlank()
                                ? "\nSede: " + conference.getVenue() + "\n" : "");
                emailPort.send(user.getEmail(), subject, body);
            } catch (final Exception e) {
                // best-effort: un fallo por asistente no debe frenar al resto
            }
        }
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
