package dev.rafex.insightbloom.moderation.adapters.inbound.http.handlers;

import dev.rafex.ether.http.core.HttpExchange;
import dev.rafex.ether.http.core.Route;
import dev.rafex.insightbloom.common.http.BaseResourceHandler;
import dev.rafex.insightbloom.moderation.application.usecases.EvaluateCensureUseCase;

import java.util.List;
import java.util.Set;

public class InternalEvaluateHandler extends BaseResourceHandler {

    private final EvaluateCensureUseCase useCase;

    public InternalEvaluateHandler(final EvaluateCensureUseCase useCase) {
        this.useCase = useCase;
    }

    @Override
    protected String basePath() {
        return "/internal/evaluate";
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
            final var result = useCase.execute(new EvaluateCensureUseCase.EvaluateRequest(
                    (String) body.get("word"), (String) body.get("detail"),
                    (String) body.get("conferenceUuid"), (String) body.get("wordCanonical"),
                    (String) body.get("messageUuid"), (String) body.get("wordText"),
                    (String) body.get("detailText"), (String) body.get("authorUuid"),
                    (String) body.get("authorDisplayName")));
            sendOk(jx, result);
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
        }
        return true;
    }
}
