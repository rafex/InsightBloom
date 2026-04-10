package dev.rafex.insightbloom.query.adapters.inbound.http.handlers;

import dev.rafex.ether.http.core.HttpExchange;
import dev.rafex.ether.http.core.Route;
import dev.rafex.ether.http.jetty12.response.JettyApiResponses;
import dev.rafex.ether.http.jetty12.exchange.JettyHttpExchange;
import dev.rafex.ether.http.jetty12.handler.NonBlockingResourceHandler;
import dev.rafex.ether.json.JsonCodec;
import dev.rafex.ether.json.JsonUtils;
import dev.rafex.insightbloom.contracts.ApiError;
import dev.rafex.insightbloom.contracts.ApiMeta;
import dev.rafex.insightbloom.contracts.ApiResponse;
import dev.rafex.insightbloom.query.application.usecases.GetCloudUseCase;
import dev.rafex.insightbloom.query.application.usecases.GetTimelineUseCase;
import dev.rafex.insightbloom.query.domain.model.MessageType;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class ConferenceQueryHandler extends NonBlockingResourceHandler {

    private static final JsonCodec JSON_CODEC = JsonUtils.codec();
    private static final JettyApiResponses RESPONSES = new JettyApiResponses(JSON_CODEC);

    private final GetCloudUseCase getCloudUseCase;
    private final GetTimelineUseCase getTimelineUseCase;

    public ConferenceQueryHandler(final GetCloudUseCase getCloudUseCase, final GetTimelineUseCase getTimelineUseCase) {
        super(JSON_CODEC);
        this.getCloudUseCase = getCloudUseCase;
        this.getTimelineUseCase = getTimelineUseCase;
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
                Route.of("/{conferenceId}/words/{word}/timeline", Set.of("GET")));
    }

    @Override
    public Set<String> supportedMethods() {
        return Set.of("GET");
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
        try {
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

    private <T> void sendOk(final JettyHttpExchange jx, final T data) {
        RESPONSES.json(jx.response(), jx.callback(), 200,
                new ApiResponse<>(data, ApiMeta.of(UUID.randomUUID().toString())));
    }

    private void sendError(final JettyHttpExchange jx, final int status, final String code, final String message) {
        RESPONSES.json(jx.response(), jx.callback(), status,
                ApiError.of(code, message, UUID.randomUUID().toString()));
    }

    private static JettyHttpExchange asJetty(final HttpExchange x) {
        return (JettyHttpExchange) x;
    }
}
