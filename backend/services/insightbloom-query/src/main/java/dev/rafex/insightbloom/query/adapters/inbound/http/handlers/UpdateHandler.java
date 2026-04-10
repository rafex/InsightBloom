package dev.rafex.insightbloom.query.adapters.inbound.http.handlers;

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
import dev.rafex.insightbloom.query.application.usecases.UpdateCloudUseCase;
import org.eclipse.jetty.server.Request;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class UpdateHandler extends NonBlockingResourceHandler {

    private static final JsonCodec JSON_CODEC = JsonUtils.codec();
    private static final JettyApiResponses RESPONSES = new JettyApiResponses(JSON_CODEC);

    private final UpdateCloudUseCase useCase;

    public UpdateHandler(final UpdateCloudUseCase useCase) {
        super(JSON_CODEC);
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
            final var body = JSON_CODEC.readValue(Request.asInputStream(jx.request()), Map.class);
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
            RESPONSES.json(jx.response(), jx.callback(), 200,
                    new ApiResponse<>(Map.of("status", "updated"), ApiMeta.of(UUID.randomUUID().toString())));
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
