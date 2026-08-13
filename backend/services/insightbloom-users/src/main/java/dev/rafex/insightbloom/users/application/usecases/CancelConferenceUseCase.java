package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.Conference;
import dev.rafex.insightbloom.users.domain.model.ConferenceStatus;
import dev.rafex.insightbloom.users.domain.model.User;
import dev.rafex.insightbloom.users.domain.ports.ConferenceRepository;
import dev.rafex.insightbloom.users.domain.ports.EmailPort;
import dev.rafex.insightbloom.users.domain.ports.ReservationRepository;
import dev.rafex.insightbloom.users.domain.ports.UserRepository;
import dev.rafex.insightbloom.users.domain.services.ConferenceCancelledEmailTemplate;

import java.util.Optional;

/**
 * Cancela un evento (distinto de CLOSED) y notifica a todos los inscritos.
 * Solo el organizador del evento puede cancelarlo.
 */
public class CancelConferenceUseCase {
    private final ConferenceRepository conferenceRepository;
    private final ReservationRepository reservationRepository;
    private final UserRepository userRepository;
    private final EmailPort emailPort;
    private final SendNotificationUseCase sendNotificationUseCase;
    private final String frontendBaseUrl;

    public CancelConferenceUseCase(final ConferenceRepository conferenceRepository,
                                    final ReservationRepository reservationRepository,
                                    final UserRepository userRepository,
                                    final EmailPort emailPort,
                                    final SendNotificationUseCase sendNotificationUseCase,
                                    final String frontendBaseUrl) {
        this.conferenceRepository = conferenceRepository;
        this.reservationRepository = reservationRepository;
        this.userRepository = userRepository;
        this.emailPort = emailPort;
        this.sendNotificationUseCase = sendNotificationUseCase;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    public Optional<Conference> execute(final String conferenceUuid, final String requestingUserUuid,
                                        final String reason) {
        return conferenceRepository.findByUuid(conferenceUuid)
                .filter(c -> c.getCreatedByUserUuid().equals(requestingUserUuid))
                .map(c -> {
                    c.setStatus(ConferenceStatus.CANCELLED);
                    conferenceRepository.save(c);
                    notifyInscritosOfCancellation(c, reason);
                    return c;
                });
    }

    private void notifyInscritosOfCancellation(final Conference conference, final String reason) {
        if (!emailPort.isEnabled()) {
            sendInAppNotificationsOnly(conference, reason);
            return;
        }

        for (final var reservation : reservationRepository.findByConference(conference.getUuid())) {
            try {
                final User user = userRepository.findByUuid(reservation.getUserUuid()).orElse(null);
                if (user == null || user.getEmail() == null || user.getEmail().isBlank()) continue;
                final String subject = "El evento " + conference.getName() + " ha sido cancelado";
                final String htmlBody = ConferenceCancelledEmailTemplate.render(conference.getName(), reason);
                emailPort.sendHtml(user.getEmail(), subject, htmlBody);
            } catch (final Exception e) {
                // best-effort: un destinatario fallido no debe impedir que el resto reciba la notificación
            }
            sendCancellationNotification(reservation.getUserUuid(), conference, reason);
        }
    }

    private void sendInAppNotificationsOnly(final Conference conference, final String reason) {
        for (final var reservation : reservationRepository.findByConference(conference.getUuid())) {
            sendCancellationNotification(reservation.getUserUuid(), conference, reason);
        }
    }

    private void sendCancellationNotification(final String userUuid, final Conference conference,
                                              final String reason) {
        try {
            if (sendNotificationUseCase == null) return;
            sendNotificationUseCase.execute(userUuid, "conference_cancelled",
                    "Cancelado: " + conference.getName(),
                    "El evento ha sido cancelado." + (reason != null && !reason.isBlank() ? " Motivo: " + reason : ""),
                    frontendBaseUrl + "/c/" + conference.getFriendlyId());
        } catch (final Exception e) {
            // best-effort
        }
    }
}
