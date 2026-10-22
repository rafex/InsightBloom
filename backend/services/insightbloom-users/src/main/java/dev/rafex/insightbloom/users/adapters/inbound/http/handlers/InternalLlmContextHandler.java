package dev.rafex.insightbloom.users.adapters.inbound.http.handlers;

import dev.rafex.ether.http.core.HttpExchange;
import dev.rafex.ether.http.core.Route;
import dev.rafex.ether.http.jetty12.exchange.JettyHttpExchange;
import dev.rafex.insightbloom.common.http.BaseResourceHandler;
import dev.rafex.insightbloom.users.adapters.outbound.llm.LlmGatewayClient;
import dev.rafex.insightbloom.users.domain.ports.ConferenceRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Issues short-lived event context assertions to the authenticated chat service. */
public final class InternalLlmContextHandler extends BaseResourceHandler {
    private final ConferenceRepository conferences;
    private final LlmGatewayClient gateway;
    private final String chatToken;

    public InternalLlmContextHandler(final ConferenceRepository conferences, final LlmGatewayClient gateway,
                                    final String chatToken) {
        this.conferences = conferences;
        this.gateway = gateway;
        this.chatToken = chatToken == null ? "" : chatToken;
    }

    @Override protected String basePath() { return "/internal/llm-context"; }
    @Override protected List<Route> routes() { return List.of(Route.of("/{eventId}/chat", Set.of("GET"))); }
    @Override public Set<String> supportedMethods() { return Set.of("GET"); }

    @Override public boolean get(final HttpExchange x) {
        final var jx = asJetty(x);
        final String supplied = jx.request().getHeaders().get("X-Service-Token");
        if (chatToken.isBlank() || supplied == null || !MessageDigest.isEqual(
                chatToken.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8))) {
            sendError(jx, 403, "forbidden", "Internal endpoint");
            return true;
        }
        final String eventId = jx.pathParam("eventId");
        try {
            if (eventId == null || !eventId.matches("[A-Za-z0-9_-]{1,128}")
                    || conferences.findByUuid(eventId).isEmpty()) {
                sendError(jx, 404, "event_not_found", "Event not found");
                return true;
            }
            sendOk(jx, Map.of("contextToken", gateway.contextTokenForEvent(eventId, "chat")));
        } catch (final Exception e) {
            sendError(jx, 503, "llm_gateway_unavailable", "LLM service unavailable");
        }
        return true;
    }
}
