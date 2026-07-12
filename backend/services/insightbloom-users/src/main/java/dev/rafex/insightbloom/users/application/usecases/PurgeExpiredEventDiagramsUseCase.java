package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.Conference;
import dev.rafex.insightbloom.users.domain.model.Timezone;
import dev.rafex.insightbloom.users.domain.ports.ConferenceRepository;
import dev.rafex.insightbloom.users.domain.ports.TimezoneRepository;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;

/**
 * Ticked periodicamente (ver scheduler en {@code UsersApplication}) para borrar el XML del
 * diagrama de drawio guardado de eventos cuya hora de fin quedo mas de 1 hora atras (TTL de
 * datos efimeros, ver DEC-0020). No borra ningun pod: drawio es una instancia compartida sin
 * estado propio, el dato vive solo en la columna {@code diagram_xml} de {@code conferences}.
 */
public class PurgeExpiredEventDiagramsUseCase {

    private static final long TTL_MINUTES_AFTER_END = 60;

    private final ConferenceRepository conferenceRepository;
    private final TimezoneRepository timezoneRepository;

    public PurgeExpiredEventDiagramsUseCase(final ConferenceRepository conferenceRepository,
                                             final TimezoneRepository timezoneRepository) {
        this.conferenceRepository = conferenceRepository;
        this.timezoneRepository = timezoneRepository;
    }

    public void execute(final Instant now) {
        for (final Conference conference : conferenceRepository.findPendingDiagramPurge()) {
            final Instant endInstant = resolveEndInstant(conference);
            if (endInstant == null) continue;
            if (Duration.between(endInstant, now).toMinutes() < TTL_MINUTES_AFTER_END) continue;

            conference.setDiagramXml(null);
            conference.setDiagramPurgedAt(now);
            conferenceRepository.save(conference);
        }
    }

    private Instant resolveEndInstant(final Conference conference) {
        try {
            final LocalDate date = LocalDate.parse(conference.getEventDate());
            final LocalTime time = LocalTime.parse(conference.getEndTime());
            final int offsetMinutes = resolveOffsetMinutes(conference);
            return date.atTime(time).toInstant(ZoneOffset.ofTotalSeconds(offsetMinutes * 60));
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
