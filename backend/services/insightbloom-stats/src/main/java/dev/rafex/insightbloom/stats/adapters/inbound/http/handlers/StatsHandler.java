package dev.rafex.insightbloom.stats.adapters.inbound.http.handlers;

import dev.rafex.ether.http.core.HttpExchange;
import dev.rafex.ether.http.core.Route;
import dev.rafex.insightbloom.common.http.BaseResourceHandler;
import dev.rafex.insightbloom.stats.application.usecases.GetStatsUseCase;

import java.util.List;
import java.util.Set;

public class StatsHandler extends BaseResourceHandler {

    private final GetStatsUseCase useCase;

    public StatsHandler(final GetStatsUseCase useCase) {
        this.useCase = useCase;
    }

    @Override
    protected String basePath() {
        return "/api/v1/conferences";
    }

    @Override
    protected List<Route> routes() {
        return List.of(
                Route.of("/{conferenceId}", Set.of("GET")),
                Route.of("/{conferenceId}/relevance", Set.of("GET")));
    }

    @Override
    public Set<String> supportedMethods() {
        return Set.of("GET");
    }

    @Override
    public boolean get(final HttpExchange x) {
        final var jx = asJetty(x);
        final String conferenceId = jx.pathParam("conferenceId");
        if (conferenceId == null) {
            sendError(jx, 400, "bad_request", "conferenceId required");
            return true;
        }
        try {
            if (jx.path().endsWith("/relevance")) {
                final String type = queryParam(jx, "type");
                sendOk(jx, useCase.relevance(conferenceId, type));
            } else {
                sendOk(jx, useCase.overview(conferenceId));
            }
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
        }
        return true;
    }
}
