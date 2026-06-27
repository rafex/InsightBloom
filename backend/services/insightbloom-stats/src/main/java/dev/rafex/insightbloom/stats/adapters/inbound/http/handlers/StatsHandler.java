package dev.rafex.insightbloom.stats.adapters.inbound.http.handlers;

import dev.rafex.ether.http.core.HttpExchange;
import dev.rafex.ether.http.core.Route;
import dev.rafex.ether.http.jetty12.exchange.JettyHttpExchange;
import dev.rafex.insightbloom.common.http.BaseResourceHandler;
import dev.rafex.insightbloom.stats.application.usecases.GetStatsUseCase;
import dev.rafex.insightbloom.stats.domain.ports.UsersPort;

import java.util.List;
import java.util.Set;

public class StatsHandler extends BaseResourceHandler {

    private final GetStatsUseCase useCase;
    private final UsersPort usersPort;

    public StatsHandler(final GetStatsUseCase useCase, final UsersPort usersPort) {
        this.useCase = useCase;
        this.usersPort = usersPort;
    }

    private boolean requireOrganizer(final JettyHttpExchange jx) {
        final String authHeader = jx.request().getHeaders().get("Authorization");
        final String token = (authHeader != null && authHeader.startsWith("Bearer ")) ? authHeader.substring(7) : null;
        if (token == null) {
            sendError(jx, 401, "unauthorized", "Missing bearer token");
            return false;
        }
        final var validation = usersPort.validate(token);
        if (!validation.valid() || !isOrganizerOrAdmin(validation.role())) {
            sendError(jx, 403, "forbidden", "Only organizers can view stats");
            return false;
        }
        return true;
    }

    private static boolean isOrganizerOrAdmin(final String role) {
        return role != null && (role.contains("organizer") || role.contains("admin"));
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
        if (!requireOrganizer(jx)) {
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
