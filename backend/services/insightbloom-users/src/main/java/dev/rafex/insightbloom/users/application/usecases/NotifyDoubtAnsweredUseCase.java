package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.Conference;
import dev.rafex.insightbloom.users.domain.model.User;
import dev.rafex.insightbloom.users.domain.ports.ConferenceRepository;
import dev.rafex.insightbloom.users.domain.ports.EmailPort;
import dev.rafex.insightbloom.users.domain.ports.TelegramNotifyPort;
import dev.rafex.insightbloom.users.domain.ports.UserRepository;

/** Notifies (email + Telegram) when the organizer answers a doubt submitted offline. */
public class NotifyDoubtAnsweredUseCase {
    private final UserRepository userRepository;
    private final ConferenceRepository conferenceRepository;
    private final EmailPort emailPort;
    private final TelegramNotifyPort telegramNotifyPort;
    private final String frontendBaseUrl;
    private final ContactInfo contact;

    public record ContactInfo(String name, String email, String linkedin, String github,
                              String blog, String telegram) {}

    public NotifyDoubtAnsweredUseCase(final UserRepository userRepository,
                                       final ConferenceRepository conferenceRepository,
                                       final EmailPort emailPort,
                                       final TelegramNotifyPort telegramNotifyPort,
                                       final String frontendBaseUrl,
                                       final ContactInfo contact) {
        this.userRepository = userRepository;
        this.conferenceRepository = conferenceRepository;
        this.emailPort = emailPort;
        this.telegramNotifyPort = telegramNotifyPort;
        this.frontendBaseUrl = frontendBaseUrl;
        this.contact = contact;
    }

    public record Request(String authorUuid, String conferenceUuid, String question, String answer) {}

    public void execute(final Request req) {
        notifyTelegram(req);

        if (!emailPort.isEnabled()) return;
        if (req.authorUuid() == null || req.authorUuid().isBlank()) return;

        final User user = userRepository.findByUuid(req.authorUuid()).orElse(null);
        if (user == null || !user.isEmailVerified() || user.getEmail() == null || user.getEmail().isBlank()) return;

        final Conference conference = conferenceRepository.findByUuid(req.conferenceUuid()).orElse(null);
        final String conferenceName = conference != null ? conference.getName() : "la conferencia";
        final String link = conference != null
                ? frontendBaseUrl + "/c/" + conference.getFriendlyId() + "/doubts"
                : frontendBaseUrl;

        final String subject = "Respondieron tu duda en " + conferenceName;
        final String body = """
                ¡Hola!

                El organizador de "%s" respondió la duda que enviaste:

                Tu pregunta:
                %s

                Respuesta:
                %s

                Puedes verla en línea aquí: %s

                — %s
                %s
                LinkedIn: %s
                GitHub: %s
                Blog: %s
                Telegram: %s
                """.formatted(
                conferenceName, req.question(), req.answer(), link,
                contact.name(), contact.email(), contact.linkedin(), contact.github(),
                contact.blog(), contact.telegram());

        try {
            emailPort.send(user.getEmail(), subject, body);
        } catch (final Exception e) {
            // best-effort: the answer itself is already saved regardless of email delivery
        }
    }

    private void notifyTelegram(final Request req) {
        if (req.conferenceUuid() == null || req.conferenceUuid().isBlank()) return;
        try {
            final String message = "💬 Respondieron una duda:\n\n%s\n\nRespuesta:\n%s".formatted(
                    req.question(), req.answer());
            telegramNotifyPort.notifyConference(req.conferenceUuid(), message);
        } catch (final Exception e) {
            // best-effort: independiente del email, nunca debe interrumpir el flujo de respuesta
        }
    }
}
