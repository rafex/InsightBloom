package dev.rafex.insightbloom.stats.adapters.inbound.http.handlers;

import dev.rafex.ether.http.core.HttpExchange;
import dev.rafex.ether.http.core.Route;
import dev.rafex.insightbloom.common.http.BaseResourceHandler;
import dev.rafex.insightbloom.stats.application.usecases.RecalcStatsUseCase;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class RecalcHandler extends BaseResourceHandler {

    private final RecalcStatsUseCase useCase;

    public RecalcHandler(final RecalcStatsUseCase useCase) {
        this.useCase = useCase;
    }

    @Override
    protected String basePath() {
        return "/internal/recalc";
    }

    @Override
    protected List<Route> routes() {
        return List.of(Route.of("/", Set.of("POST")));
    }

    @Override
    public Set<String> supportedMethods() {
        return Set.of("POST");
    }

    @Override
    public boolean post(final HttpExchange x) {
        final var jx = asJetty(x);
        try {
            final var body = parseBody(jx);
            useCase.execute(new RecalcStatsUseCase.RecalcRequest(
                    (String) body.get("conferenceUuid"),
                    (String) body.get("wordNormalized"),
                    (String) body.get("wordCanonical"),
                    (String) body.get("messageType"),
                    (String) body.get("wordIntent"),
                    Boolean.TRUE.equals(body.get("visible"))));
            sendOk(jx, Map.of("status", "recalculated"));
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
        }
        return true;
    }
}
