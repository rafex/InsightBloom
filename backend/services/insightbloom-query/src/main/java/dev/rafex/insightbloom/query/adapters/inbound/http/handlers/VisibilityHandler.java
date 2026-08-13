package dev.rafex.insightbloom.query.adapters.inbound.http.handlers;

import dev.rafex.ether.http.core.HttpExchange;
import dev.rafex.ether.http.core.Route;
import dev.rafex.insightbloom.common.http.BaseResourceHandler;
import dev.rafex.insightbloom.query.application.usecases.SetVisibilityUseCase;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class VisibilityHandler extends BaseResourceHandler {

    private final SetVisibilityUseCase useCase;

    public VisibilityHandler(final SetVisibilityUseCase useCase) {
        this.useCase = useCase;
    }

    @Override
    protected String basePath() {
        return "/internal/visibility";
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
        if (!validInternalAuth(jx)) {
            sendError(jx, 403, "forbidden", "Internal endpoint");
            return true;
        }
        try {
            final var body = parseBody(jx);
            useCase.execute(new SetVisibilityUseCase.Request(
                    (String) body.get("conferenceUuid"),
                    (String) body.get("wordNormalized"),
                    Boolean.TRUE.equals(body.get("visible"))));
            sendOk(jx, Map.of("status", "updated"));
        } catch (final Exception e) {
            sendInternalError(jx, e);
        }
        return true;
    }
}
