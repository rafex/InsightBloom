package dev.rafex.insightbloom.query.adapters.inbound.http.handlers;

import dev.rafex.ether.http.core.HttpExchange;
import dev.rafex.ether.http.core.Route;
import dev.rafex.ether.http.jetty12.exchange.JettyHttpExchange;
import dev.rafex.ether.json.JsonUtils;
import dev.rafex.insightbloom.common.http.BaseResourceHandler;
import dev.rafex.insightbloom.query.application.usecases.DeleteConferenceDataUseCase;
import dev.rafex.insightbloom.query.application.usecases.GetCloudUseCase;
import dev.rafex.insightbloom.query.application.usecases.GetTimelineUseCase;
import dev.rafex.insightbloom.query.domain.model.MessageType;
import dev.rafex.insightbloom.query.domain.ports.CloudEventBus;
import dev.rafex.insightbloom.query.domain.ports.ConferenceLifecyclePort;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ConferenceQueryHandler extends BaseResourceHandler {

    private static final long HEARTBEAT_SECONDS = 25;

    private final GetCloudUseCase getCloudUseCase;
    private final GetTimelineUseCase getTimelineUseCase;
    private final DeleteConferenceDataUseCase deleteConferenceDataUseCase;
    private final CloudEventBus cloudEventBus;
    private final ScheduledExecutorService scheduler;
    private final ConferenceLifecyclePort conferenceLifecyclePort;

    public ConferenceQueryHandler(final GetCloudUseCase getCloudUseCase, final GetTimelineUseCase getTimelineUseCase,
                                   final DeleteConferenceDataUseCase deleteConferenceDataUseCase,
                                   final CloudEventBus cloudEventBus, final ScheduledExecutorService scheduler,
                                   final ConferenceLifecyclePort conferenceLifecyclePort) {
        this.getCloudUseCase = getCloudUseCase;
        this.getTimelineUseCase = getTimelineUseCase;
        this.deleteConferenceDataUseCase = deleteConferenceDataUseCase;
        this.cloudEventBus = cloudEventBus;
        this.scheduler = scheduler;
        this.conferenceLifecyclePort = conferenceLifecyclePort;
    }

    @Override
    protected String basePath() {
        return "/api/v1/conferences";
    }

    @Override
    protected List<Route> routes() {
        return List.of(
                Route.of("/{conferenceId}/cloud/doubts", Set.of("GET")),
                Route.of("/{conferenceId}/cloud/topics", Set.of("GET")),
                Route.of("/{conferenceId}/cloud/doubts/stream", Set.of("GET")),
                Route.of("/{conferenceId}/cloud/topics/stream", Set.of("GET")),
                Route.of("/{conferenceId}/words/{word}/timeline", Set.of("GET")),
                Route.of("/{conferenceId}/cloud", Set.of("DELETE")));
    }

    @Override
    public Set<String> supportedMethods() {
        return Set.of("GET", "DELETE");
    }

    @Override
    public boolean delete(final HttpExchange x) {
        final var jx = asJetty(x);
        if (!validInternalAuth(jx)) { sendError(jx, 403, "forbidden", "Internal access only"); return true; }
        try {
            deleteConferenceDataUseCase.execute(jx.pathParam("conferenceId"));
            sendOk(jx, Map.of("status", "deleted"));
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", "Internal error");
        }
        return true;
    }

    @Override
    public boolean get(final HttpExchange x) {
        final var jx = asJetty(x);
        final String path = jx.path();
        final String conferenceId = jx.pathParam("conferenceId");

        if (conferenceId == null) {
            sendError(jx, 400, "bad_request", "Invalid path");
            return true;
        }
        // AUD-06: sin esto, cualquiera con el UUID podia leer la nube/timeline de una
        // conferencia desactivada, vencida o inexistente sin ninguna credencial. La
        // visibilidad PRIVATE/PUBLIC no se filtra aca a proposito -- este endpoint lo usan
        // asistentes de eventos privados sin login (identidad por fingerprint de invitado),
        // solo el ciclo de vida (activo, no vencido) es lo que faltaba validar.
        if (!conferenceLifecyclePort.isActive(conferenceId)) {
            sendError(jx, 404, "conference_not_found", "Conference not found or not active");
            return true;
        }
        try {
            if (path.endsWith("/cloud/doubts/stream")) {
                streamCloud(jx, conferenceId, MessageType.DOUBT);
                return true;
            }
            if (path.endsWith("/cloud/topics/stream")) {
                streamCloud(jx, conferenceId, MessageType.TOPIC);
                return true;
            }
            if (path.endsWith("/cloud/doubts")) {
                sendOk(jx, getCloudUseCase.execute(conferenceId, MessageType.DOUBT));
                return true;
            }
            if (path.endsWith("/cloud/topics")) {
                sendOk(jx, getCloudUseCase.execute(conferenceId, MessageType.TOPIC));
                return true;
            }
            if (path.contains("/words/") && path.endsWith("/timeline")) {
                final String rawWord = jx.pathParam("word");
                final String word = rawWord != null ? URLDecoder.decode(rawWord, StandardCharsets.UTF_8) : null;
                if (word == null) { sendError(jx, 400, "bad_request", "word required"); return true; }
                sendOk(jx, getTimelineUseCase.execute(conferenceId, word));
                return true;
            }
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
            return true;
        }
        sendError(jx, 404, "not_found", "Endpoint not found");
        return true;
    }

    /**
     * Opens an SSE stream: sends the current cloud as a "snapshot" event, then an "update" event
     * for every subsequent NATS message on the conference+type subject (published by
     * {@code UpdateCloudUseCase} — possibly from a different replica). A periodic comment keeps
     * the connection alive through proxies/timeouts.
     */
    private void streamCloud(final JettyHttpExchange jx, final String conferenceId, final MessageType type) {
        final var stream = jx.startEventStream();
        stream.send("snapshot", JsonUtils.toJson(getCloudUseCase.execute(conferenceId, type)));

        final AutoCloseable subscription = cloudEventBus.subscribe(conferenceId, type,
                payload -> stream.send("update", JsonUtils.toJson(payload)));
        final var heartbeat = scheduler.scheduleAtFixedRate(() -> stream.comment("ping"),
                HEARTBEAT_SECONDS, HEARTBEAT_SECONDS, TimeUnit.SECONDS);

        stream.onClose(() -> {
            heartbeat.cancel(true);
            try {
                subscription.close();
            } catch (final Exception ignored) {
                // closing an already-dead NATS dispatcher — nothing to do
            }
        });
    }
}
