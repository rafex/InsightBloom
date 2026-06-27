package dev.rafex.insightbloom.query.adapters.inbound.http.handlers;

import dev.rafex.ether.http.core.HttpExchange;
import dev.rafex.ether.http.core.Route;
import dev.rafex.insightbloom.common.http.BaseResourceHandler;
import dev.rafex.insightbloom.query.application.usecases.SetMessageVisibilityUseCase;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class MessageVisibilityHandler extends BaseResourceHandler {

    private final SetMessageVisibilityUseCase useCase;

    public MessageVisibilityHandler(final SetMessageVisibilityUseCase useCase) {
        this.useCase = useCase;
    }

    @Override
    protected String basePath() {
        return "/internal/message-visibility";
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
            useCase.execute(new SetMessageVisibilityUseCase.Request(
                    (String) body.get("messageUuid"),
                    Boolean.TRUE.equals(body.get("visible"))));
            sendOk(jx, Map.of("status", "updated"));
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
        }
        return true;
    }
}
