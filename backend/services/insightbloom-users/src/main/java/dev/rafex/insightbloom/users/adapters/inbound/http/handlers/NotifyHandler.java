package dev.rafex.insightbloom.users.adapters.inbound.http.handlers;

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
import dev.rafex.insightbloom.users.application.usecases.NotifyDoubtAnsweredUseCase;
import org.eclipse.jetty.server.Request;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Internal, service-to-service endpoint (no end-user auth): triggered by insightbloom-moderation. */
public class NotifyHandler extends NonBlockingResourceHandler {

    private static final JsonCodec JSON_CODEC = JsonUtils.codec();
    private static final JettyApiResponses RESPONSES = new JettyApiResponses(JSON_CODEC);

    private final NotifyDoubtAnsweredUseCase notifyDoubtAnsweredUseCase;

    public NotifyHandler(final NotifyDoubtAnsweredUseCase notifyDoubtAnsweredUseCase) {
        super(JSON_CODEC);
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
            final var body = JSON_CODEC.readValue(Request.asInputStream(jx.request()), Map.class);
            notifyDoubtAnsweredUseCase.execute(new NotifyDoubtAnsweredUseCase.Request(
                    (String) body.get("authorUuid"), (String) body.get("conferenceUuid"),
                    (String) body.get("question"), (String) body.get("answer")));
            RESPONSES.json(jx.response(), jx.callback(), 200,
                    new ApiResponse<>(Map.of("status", "queued"), ApiMeta.of(UUID.randomUUID().toString())));
        } catch (final Exception e) {
            RESPONSES.json(jx.response(), jx.callback(), 500,
                    ApiError.of("internal_error", e.getMessage(), UUID.randomUUID().toString()));
        }
        return true;
    }

    private static JettyHttpExchange asJetty(final HttpExchange x) {
        return (JettyHttpExchange) x;
    }
}
