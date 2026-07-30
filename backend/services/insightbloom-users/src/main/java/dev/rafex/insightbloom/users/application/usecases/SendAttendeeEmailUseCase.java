package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.Conference;
import dev.rafex.insightbloom.users.domain.model.User;
import dev.rafex.insightbloom.users.domain.ports.ConferenceRepository;
import dev.rafex.insightbloom.users.domain.ports.EmailPort;
import dev.rafex.insightbloom.users.domain.ports.ReservationRepository;
import dev.rafex.insightbloom.users.domain.ports.UserRepository;
import dev.rafex.insightbloom.users.domain.services.AttendeeEmailTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Envía un correo libre del organizador a los inscritos de un evento (masivo o a un subconjunto
 * puntual). "Inscrito" se resuelve vía {@link ReservationRepository} (boleto/Reservation), no
 * ConferenceMembership -- ver regla de negocio 2026-07-18.
 */
public class SendAttendeeEmailUseCase {
    private static final int MAX_SUBJECT_LENGTH = 200;
    private static final int MAX_MESSAGE_LENGTH = 4000;

    private final ConferenceRepository conferenceRepository;
    private final ReservationRepository reservationRepository;
    private final UserRepository userRepository;
    private final EmailPort emailPort;

    public SendAttendeeEmailUseCase(final ConferenceRepository conferenceRepository,
                                     final ReservationRepository reservationRepository,
                                     final UserRepository userRepository,
                                     final EmailPort emailPort) {
        this.conferenceRepository = conferenceRepository;
        this.reservationRepository = reservationRepository;
        this.userRepository = userRepository;
        this.emailPort = emailPort;
    }

    public record SendRequest(String subject, String message, String format, List<String> recipientUuids) {}

    public record SendSummary(int sent, int skipped) {}

    public SendSummary execute(final String conferenceUuid, final SendRequest request) {
        final String subject = blankToNull(request.subject());
        final String message = blankToNull(request.message());
        if (subject == null || subject.length() > MAX_SUBJECT_LENGTH) {
            throw new IllegalArgumentException("subject_invalid");
        }
        if (message == null || message.length() > MAX_MESSAGE_LENGTH) {
            throw new IllegalArgumentException("message_invalid");
        }
        final String format = formatOrDefault(request.format());
        if (!Set.of("text", "html", "markdown").contains(format)) {
            throw new IllegalArgumentException("format_invalid");
        }
        final Conference conference = conferenceRepository.findByUuid(conferenceUuid)
                .orElseThrow(() -> new IllegalArgumentException("conference_not_found"));
        if (!emailPort.isEnabled()) throw new IllegalStateException("email_provider_not_configured");

        final Map<String, String> emailByUserUuid = new LinkedHashMap<>();
        for (final var reservation : reservationRepository.findByConference(conferenceUuid)) {
            userRepository.findByUuid(reservation.getUserUuid())
                    .map(User::getEmail)
                    .filter(email -> email != null && !email.isBlank())
                    .ifPresent(email -> emailByUserUuid.putIfAbsent(reservation.getUserUuid(), email));
        }

        final List<String> requestedUuids = request.recipientUuids();
        if (requestedUuids != null && !requestedUuids.isEmpty()) {
            final Set<String> allowed = Set.copyOf(requestedUuids);
            emailByUserUuid.keySet().retainAll(allowed);
        }
        if (emailByUserUuid.isEmpty()) throw new IllegalArgumentException("no_recipients");

        final String html = AttendeeEmailTemplate.render(conference.getName(), subject, message, format);
        int sent = 0;
        int skipped = 0;
        for (final String email : Set.copyOf(emailByUserUuid.values())) {
            try {
                emailPort.sendHtml(email, subject, html);
                sent++;
            } catch (final Exception e) {
                skipped++;
            }
        }
        return new SendSummary(sent, skipped);
    }

    private static String blankToNull(final String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private static String formatOrDefault(final String format) {
        return (format == null || format.isBlank()) ? "text" : format.trim().toLowerCase();
    }
}
