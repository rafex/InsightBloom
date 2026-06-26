package dev.rafex.insightbloom.query.adapters.inbound.http.handlers;

import dev.rafex.ether.http.core.HttpExchange;
import dev.rafex.ether.http.core.Route;
import dev.rafex.insightbloom.common.http.BaseResourceHandler;
import dev.rafex.insightbloom.query.application.usecases.UpdateCloudUseCase;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class UpdateHandler extends BaseResourceHandler {

    private final UpdateCloudUseCase useCase;

    public UpdateHandler(final UpdateCloudUseCase useCase) {
        this.useCase = useCase;
    }

    @Override
    protected String basePath() {
        return "/internal/update";
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
            useCase.execute(new UpdateCloudUseCase.UpdateRequest(
                    (String) body.get("conferenceUuid"),
                    (String) body.get("wordNormalized"),
                    (String) body.get("wordCanonical"),
                    (String) body.get("messageType"),
                    body.containsKey("relevanceScore") ? ((Number) body.get("relevanceScore")).doubleValue() : 0.0,
                    body.containsKey("messageCount") ? ((Number) body.get("messageCount")).longValue() : 0L,
                    (String) body.get("messageUuid"),
                    (String) body.get("authorLabel"),
                    (String) body.getOrDefault("authorKind", "GUEST"),
                    (String) body.get("detailVisible"),
                    (String) body.get("receivedAt"),
                    Boolean.TRUE.equals(body.get("wordVisible"))));
            sendOk(jx, Map.of("status", "updated"));
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
        }
        return true;
    }
}
