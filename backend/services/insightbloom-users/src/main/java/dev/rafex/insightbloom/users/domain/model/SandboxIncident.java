package dev.rafex.insightbloom.users.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Fase C (DEC-0025): registro de un asiento de un Pod "neovim" compartido que el watchdog del
 * seat-agent tuvo que matar/reiniciar por abuso de recursos (CPU/memoria sostenido, o sospecha
 * de fork-bomb) -- ver {@code sandbox-agent.py}. Reportado por el propio Pod al endpoint interno
 * {@code POST /internal/sandbox-incidents} (autenticado con {@code SANDBOX_INCIDENT_REPORT_KEY},
 * un secreto de bajo privilegio, no el {@code INTERNAL_API_KEY} de plataforma -- ver DEC-0025),
 * para que el organizador de la conferencia lo vea en el Dashboard y sepa que alumno esta
 * causando problemas.
 */
public class SandboxIncident {
    private final String uuid;
    private final String conferenceUuid;
    private final String podName;
    private final int seatIndex;
    private final String userUuid; // nullable: el asiento puede no tener alumno confirmado todavia
    private final String type; // cpu_abuse | memory_abuse | fork_bomb_suspected
    private final String detail;
    private final Instant occurredAt;

    public SandboxIncident(final String conferenceUuid, final String podName, final int seatIndex,
                            final String userUuid, final String type, final String detail) {
        this.uuid = UUID.randomUUID().toString();
        this.conferenceUuid = conferenceUuid;
        this.podName = podName;
        this.seatIndex = seatIndex;
        this.userUuid = userUuid;
        this.type = type;
        this.detail = detail;
        this.occurredAt = Instant.now();
    }

    public SandboxIncident(final String uuid, final String conferenceUuid, final String podName,
                            final int seatIndex, final String userUuid, final String type,
                            final String detail, final Instant occurredAt) {
        this.uuid = uuid;
        this.conferenceUuid = conferenceUuid;
        this.podName = podName;
        this.seatIndex = seatIndex;
        this.userUuid = userUuid;
        this.type = type;
        this.detail = detail;
        this.occurredAt = occurredAt;
    }

    public String getUuid() { return uuid; }
    public String getConferenceUuid() { return conferenceUuid; }
    public String getPodName() { return podName; }
    public int getSeatIndex() { return seatIndex; }
    public String getUserUuid() { return userUuid; }
    public String getType() { return type; }
    public String getDetail() { return detail; }
    public Instant getOccurredAt() { return occurredAt; }
}
