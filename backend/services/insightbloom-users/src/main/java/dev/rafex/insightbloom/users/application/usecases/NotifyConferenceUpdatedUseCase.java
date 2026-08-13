package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.Conference;
import dev.rafex.insightbloom.users.domain.model.User;
import dev.rafex.insightbloom.users.domain.ports.EmailPort;
import dev.rafex.insightbloom.users.domain.ports.EventTypeRepository;
import dev.rafex.insightbloom.users.domain.ports.ReservationRepository;
import dev.rafex.insightbloom.users.domain.ports.UserRepository;
import dev.rafex.insightbloom.users.domain.services.ConferenceUpdateEmailTemplate;
import dev.rafex.insightbloom.users.domain.services.ConferenceUpdateEmailTemplate.FieldRow;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Notifica a los inscritos de un evento (vía Reservation, no ConferenceMembership -- regla
 * 2026-07-18) cuando el organizador modifica datos clave. El correo siempre muestra el snapshot
 * completo del evento, resaltando qué campos cambiaron en esta corrida.
 */
public class NotifyConferenceUpdatedUseCase {
    private final ReservationRepository reservationRepository;
    private final UserRepository userRepository;
    private final EmailPort emailPort;
    private final EventTypeRepository eventTypeRepository;
    private final SendNotificationUseCase sendNotificationUseCase;
    private final String frontendBaseUrl;

    public NotifyConferenceUpdatedUseCase(final ReservationRepository reservationRepository,
                                           final UserRepository userRepository,
                                           final EmailPort emailPort,
                                           final EventTypeRepository eventTypeRepository) {
        this(reservationRepository, userRepository, emailPort, eventTypeRepository, null, null);
    }

    public NotifyConferenceUpdatedUseCase(final ReservationRepository reservationRepository,
                                           final UserRepository userRepository,
                                           final EmailPort emailPort,
                                           final EventTypeRepository eventTypeRepository,
                                           final SendNotificationUseCase sendNotificationUseCase,
                                           final String frontendBaseUrl) {
        this.reservationRepository = reservationRepository;
        this.userRepository = userRepository;
        this.emailPort = emailPort;
        this.eventTypeRepository = eventTypeRepository;
        this.sendNotificationUseCase = sendNotificationUseCase;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    public void execute(final Conference before, final Conference after) {
        final List<FieldRow> rows = buildRows(before, after);
        if (rows.stream().noneMatch(FieldRow::changed)) return;

        final List<String> userUuids = new ArrayList<>();
        for (final var reservation : reservationRepository.findByConference(after.getUuid())) {
            userUuids.add(reservation.getUserUuid());
        }
        if (userUuids.isEmpty()) return;

        if (emailPort.isEnabled()) {
            final Set<String> emails = new LinkedHashSet<>();
            for (final String userUuid : userUuids) {
                userRepository.findByUuid(userUuid)
                        .map(User::getEmail)
                        .filter(email -> email != null && !email.isBlank())
                        .ifPresent(emails::add);
            }
            if (!emails.isEmpty()) {
                final String html = ConferenceUpdateEmailTemplate.render(after.getName(), rows);
                final String subject = "Cambios en el evento: " + after.getName();
                for (final String email : emails) {
                    try {
                        emailPort.sendHtml(email, subject, html);
                    } catch (final Exception ignored) {
                        // Un destinatario fallido no debe impedir que el resto reciba la notificación.
                    }
                }
            }
        }

        sendInAppNotifications(userUuids, after);
    }

    private void sendInAppNotifications(final List<String> userUuids, final Conference conference) {
        if (sendNotificationUseCase == null || frontendBaseUrl == null) return;
        final String summary = buildChangesSummary(conference);
        for (final String userUuid : userUuids) {
            try {
                sendNotificationUseCase.execute(userUuid, "conference_updated",
                        "Cambios en " + conference.getName(),
                        summary,
                        frontendBaseUrl + "/c/" + conference.getFriendlyId());
            } catch (final Exception ignored) {
                // best-effort: fallos en notificación in-app no deben afectar a otros usuarios
            }
        }
    }

    private String buildChangesSummary(final Conference conference) {
        return "El organizador ha actualizado los detalles del evento. Revisa los cambios.";
    }

    private List<FieldRow> buildRows(final Conference before, final Conference after) {
        final List<FieldRow> rows = new ArrayList<>();
        rows.add(row("Nombre", before.getName(), after.getName()));
        rows.add(row("Descripción", before.getDescription(), after.getDescription()));
        rows.add(row("Cronograma", before.getScheduleMarkdown(), after.getScheduleMarkdown()));
        rows.add(row("Fecha", before.getEventDate(), after.getEventDate()));
        rows.add(row("Horario", schedule(before), schedule(after)));
        rows.add(row("Precio", price(before), price(after)));
        rows.add(row("Tipo de evento", eventTypeName(before), eventTypeName(after)));
        rows.add(row("Ubicación", location(before), location(after)));
        return rows;
    }

    private static FieldRow row(final String label, final String oldValue, final String newValue) {
        return new FieldRow(label, oldValue, newValue, !Objects.equals(oldValue, newValue));
    }

    private static String schedule(final Conference conference) {
        final String start = conference.getStartTime();
        final String end = conference.getEndTime();
        if (start == null && end == null) return null;
        return (start == null ? "?" : start) + " - " + (end == null ? "?" : end);
    }

    private static String price(final Conference conference) {
        final String price = conference.getTicketPrice();
        final String currency = conference.getTicketCurrency();
        if (price == null) return null;
        return price + " " + (currency == null ? "" : currency);
    }

    private String eventTypeName(final Conference conference) {
        final String key = conference.getEventTypeKey();
        if (key == null) return null;
        return eventTypeRepository.findByKey(key).map(t -> t.getName()).orElse(key);
    }

    private static String location(final Conference conference) {
        final Double lat = conference.getLatitude();
        final Double lon = conference.getLongitude();
        final String venue = conference.getVenue();
        if (lat == null && lon == null && venue == null) return null;
        final String coords = (lat == null || lon == null) ? "" : " (" + lat + ", " + lon + ")";
        return (venue == null ? "" : venue) + coords;
    }
}
