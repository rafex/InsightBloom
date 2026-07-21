package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.Conference;
import dev.rafex.insightbloom.users.domain.model.Timezone;
import dev.rafex.insightbloom.users.domain.ports.ConferenceRepository;
import dev.rafex.insightbloom.users.domain.ports.EtherpadPort;
import dev.rafex.insightbloom.users.domain.ports.TimezoneRepository;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;

/**
 * Ticked periodicamente (ver scheduler en {@code UsersApplication}) para borrar el pad de
 * Etherpad de eventos cuya hora de fin quedo mas de 1 hora atras (TTL de datos efimeros, ver
 * DEC-0020) — el pad, no el pod: la instancia de Etherpad sigue corriendo y compartida entre
 * eventos. Borra tanto el pad grupal como los pads individuales. Cada evento se purga como
 * maximo una vez, marcado via {@code notes_purged_at}.
 */
public class PurgeExpiredEventNotesUseCase {

    private static final long TTL_MINUTES_AFTER_END = 60;

    private final ConferenceRepository conferenceRepository;
    private final TimezoneRepository timezoneRepository;
    private final EtherpadPort etherpadPort;

    public PurgeExpiredEventNotesUseCase(final ConferenceRepository conferenceRepository,
                                          final TimezoneRepository timezoneRepository,
                                          final EtherpadPort etherpadPort) {
        this.conferenceRepository = conferenceRepository;
        this.timezoneRepository = timezoneRepository;
        this.etherpadPort = etherpadPort;
    }

    public void execute(final Instant now) {
        for (final Conference conference : conferenceRepository.findPendingNotesPurge()) {
            final Instant endInstant = resolveEndInstant(conference);
            if (endInstant == null) continue;
            if (Duration.between(endInstant, now).toMinutes() < TTL_MINUTES_AFTER_END) continue;

            try {
                etherpadPort.deletePadsForConference(conference.getUuid());
            } catch (final Exception e) {
                // best-effort: un fallo de red no debe frenar la purga de otros eventos
            }
            conference.setNotesPurgedAt(now);
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
