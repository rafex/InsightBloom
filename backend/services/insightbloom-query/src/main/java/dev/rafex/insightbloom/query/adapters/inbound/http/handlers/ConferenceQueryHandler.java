package dev.rafex.insightbloom.query.adapters.inbound.http.handlers;

import dev.rafex.ether.http.core.HttpExchange;
import dev.rafex.ether.http.core.Route;
import dev.rafex.insightbloom.common.http.BaseResourceHandler;
import dev.rafex.insightbloom.query.application.usecases.DeleteConferenceDataUseCase;
import dev.rafex.insightbloom.query.application.usecases.GetCloudUseCase;
import dev.rafex.insightbloom.query.application.usecases.GetTimelineUseCase;
import dev.rafex.insightbloom.query.domain.model.MessageType;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ConferenceQueryHandler extends BaseResourceHandler {

    private final GetCloudUseCase getCloudUseCase;
    private final GetTimelineUseCase getTimelineUseCase;
    private final DeleteConferenceDataUseCase deleteConferenceDataUseCase;

    public ConferenceQueryHandler(final GetCloudUseCase getCloudUseCase, final GetTimelineUseCase getTimelineUseCase,
                                   final DeleteConferenceDataUseCase deleteConferenceDataUseCase) {
        this.getCloudUseCase = getCloudUseCase;
        this.getTimelineUseCase = getTimelineUseCase;
        this.deleteConferenceDataUseCase = deleteConferenceDataUseCase;
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
        try {
            deleteConferenceDataUseCase.execute(jx.pathParam("conferenceId"));
            sendOk(jx, Map.of("status", "deleted"));
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
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
}
