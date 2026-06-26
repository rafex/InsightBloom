package dev.rafex.insightbloom.users.adapters.inbound.http.handlers;

import dev.rafex.ether.http.core.HttpExchange;
import dev.rafex.ether.http.core.Route;
import dev.rafex.insightbloom.common.http.BaseResourceHandler;
import dev.rafex.insightbloom.users.application.usecases.NotifyDoubtAnsweredUseCase;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Internal, service-to-service endpoint (no end-user auth): triggered by insightbloom-moderation. */
public class NotifyHandler extends BaseResourceHandler {

    private final NotifyDoubtAnsweredUseCase notifyDoubtAnsweredUseCase;

    public NotifyHandler(final NotifyDoubtAnsweredUseCase notifyDoubtAnsweredUseCase) {
        this.notifyDoubtAnsweredUseCase = notifyDoubtAnsweredUseCase;
    }

    @Override
    protected String basePath() {
        return "/api/v1/notify";
    }

    @Override
    protected List<Route> routes() {
        return List.of(Route.of("/doubt-answered", Set.of("POST")));
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
            notifyDoubtAnsweredUseCase.execute(new NotifyDoubtAnsweredUseCase.Request(
                    (String) body.get("authorUuid"), (String) body.get("conferenceUuid"),
                    (String) body.get("question"), (String) body.get("answer")));
            sendOk(jx, Map.of("status", "queued"));
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
        }
        return true;
    }
}
