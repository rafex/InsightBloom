package dev.rafex.insightbloom.users.domain.services;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

public class IcsCalendarBuilder {

    public static class CalendarEventInput {
        public final String name;
        public final String eventDate; // ISO-8601: yyyy-MM-dd
        public final String startTime; // HH:mm (24h)
        public final String endTime; // nullable, HH:mm
        public final String venue; // nullable, texto libre
        public final Integer utcOffsetMinutes; // nullable, default 0

        public CalendarEventInput(String name, String eventDate, String startTime,
                                  String endTime, String venue, Integer utcOffsetMinutes) {
            this.name = name;
            this.eventDate = eventDate;
            this.startTime = startTime;
            this.endTime = endTime;
            this.venue = venue;
            this.utcOffsetMinutes = utcOffsetMinutes;
        }
    }

    private static Instant toUtcInstant(String eventDate, String time, Integer utcOffsetMinutes) {
        LocalDate date = LocalDate.parse(eventDate);
        LocalTime localTime = time != null && !time.isBlank() ? LocalTime.parse(time) : LocalTime.of(0, 0);
        ZoneOffset offset = ZoneOffset.ofTotalSeconds((utcOffsetMinutes != null ? utcOffsetMinutes : 0) * 60);
        return date.atTime(localTime).atOffset(offset).toInstant();
    }

    private static String formatUtc(Instant instant) {
        return instant.toString().replace("-", "").replace(":", "").split("\\.")[0] + "Z";
    }

    private static String escapeIcsText(String text) {
        if (text == null || text.isBlank()) return "";
        return text.replace("\\", "\\\\")
                   .replace(";", "\\;")
                   .replace(",", "\\,")
                   .replace("\n", "\\n");
    }

    /**
     * Genera el contenido de un archivo .ics para el evento dado.
     * Reusable en emails como attachment.
     */
    public static String buildIcs(CalendarEventInput event) {
        Instant start = toUtcInstant(event.eventDate, event.startTime, event.utcOffsetMinutes);
        Instant end = event.endTime != null && !event.endTime.isBlank()
                ? toUtcInstant(event.eventDate, event.endTime, event.utcOffsetMinutes)
                : start.plusSeconds(3600); // 1h por defecto si no hay hora de fin

        List<String> lines = new ArrayList<>();
        lines.add("BEGIN:VCALENDAR");
        lines.add("VERSION:2.0");
        lines.add("PRODID:-//InsightBloom//Conferencia//ES");
        lines.add("BEGIN:VEVENT");
        lines.add("UID:" + System.currentTimeMillis() + "@insightbloom");
        lines.add("DTSTAMP:" + formatUtc(Instant.now()));
        lines.add("DTSTART:" + formatUtc(start));
        lines.add("DTEND:" + formatUtc(end));
        lines.add("SUMMARY:" + escapeIcsText(event.name));
        if (event.venue != null && !event.venue.isBlank()) {
            lines.add("LOCATION:" + escapeIcsText(event.venue));
        }
        lines.add("END:VEVENT");
        lines.add("END:VCALENDAR");

        return String.join("\r\n", lines);
    }
}
