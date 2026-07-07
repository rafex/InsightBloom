package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.Conference;
import dev.rafex.insightbloom.users.domain.model.ConferenceMembership;
import dev.rafex.insightbloom.users.domain.model.User;
import dev.rafex.insightbloom.users.domain.ports.ConferenceMembershipRepository;
import dev.rafex.insightbloom.users.domain.ports.EmailPort;
import dev.rafex.insightbloom.users.domain.ports.TimezoneRepository;
import dev.rafex.insightbloom.users.domain.ports.UserRepository;

public class JoinConferenceUseCase {
    private final GetConferenceUseCase getConferenceUseCase;
    private final ConferenceMembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final EmailPort emailPort;
    private final TimezoneRepository timezoneRepository;

    public JoinConferenceUseCase(final GetConferenceUseCase getConferenceUseCase,
                                  final ConferenceMembershipRepository membershipRepository,
                                  final UserRepository userRepository,
                                  final EmailPort emailPort,
                                  final TimezoneRepository timezoneRepository) {
        this.getConferenceUseCase = getConferenceUseCase;
        this.membershipRepository = membershipRepository;
        this.userRepository = userRepository;
        this.emailPort = emailPort;
        this.timezoneRepository = timezoneRepository;
    }

    public Conference execute(final String userUuid, final String identifier) {
        final Conference conference = getConferenceUseCase.resolveAny(identifier)
                .orElseThrow(() -> new IllegalArgumentException("conference_not_found"));
        final boolean firstJoin = !membershipRepository.exists(userUuid, conference.getUuid());
        membershipRepository.recordJoin(new ConferenceMembership(
                userUuid, conference.getUuid(), conference.getName(), conference.getFriendlyId()));
        if (firstJoin) {
            sendConfirmationEmail(userUuid, conference);
        }
        return conference;
    }

    /** Best-effort: un fallo de correo nunca debe interrumpir el registro a la conferencia. */
    private void sendConfirmationEmail(final String userUuid, final Conference conference) {
        try {
            if (!emailPort.isEnabled()) return;
            final User user = userRepository.findByUuid(userUuid).orElse(null);
            if (user == null || user.getEmail() == null || user.getEmail().isBlank()) return;

            final String schedule = describeSchedule(conference);
            final String subject = "Te has apuntado a la conferencia " + conference.getName();
            final String body = """
                    ¡Hola!

                    Confirmamos tu registro en "%s".
                    %s%s
                    Nos vemos ahí.
                    """.formatted(
                    conference.getName(),
                    schedule.isBlank() ? "" : "\n" + schedule + "\n",
                    conference.getVenue() != null && !conference.getVenue().isBlank()
                            ? "\nSede: " + conference.getVenue() + "\n" : "");
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
