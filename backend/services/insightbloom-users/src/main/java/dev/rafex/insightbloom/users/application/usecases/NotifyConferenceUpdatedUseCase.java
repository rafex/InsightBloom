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

    public NotifyConferenceUpdatedUseCase(final ReservationRepository reservationRepository,
                                           final UserRepository userRepository,
                                           final EmailPort emailPort,
                                           final EventTypeRepository eventTypeRepository) {
        this.reservationRepository = reservationRepository;
        this.userRepository = userRepository;
        this.emailPort = emailPort;
        this.eventTypeRepository = eventTypeRepository;
    }

    public void execute(final Conference before, final Conference after) {
        final List<FieldRow> rows = buildRows(before, after);
        if (rows.stream().noneMatch(FieldRow::changed)) return;
        if (!emailPort.isEnabled()) return;

        final Set<String> emails = new LinkedHashSet<>();
        for (final var reservation : reservationRepository.findByConference(after.getUuid())) {
            userRepository.findByUuid(reservation.getUserUuid())
                    .map(User::getEmail)
                    .filter(email -> email != null && !email.isBlank())
                    .ifPresent(emails::add);
        }
        if (emails.isEmpty()) return;

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
