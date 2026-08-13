package dev.rafex.insightbloom.ingest.adapters.inbound.http.handlers;

import dev.rafex.ether.http.core.HttpExchange;
import dev.rafex.ether.http.core.Route;
import dev.rafex.ether.http.jetty12.exchange.JettyHttpExchange;
import dev.rafex.insightbloom.common.http.BaseResourceHandler;
import dev.rafex.insightbloom.ingest.application.usecases.DeleteConferenceDataUseCase;
import dev.rafex.insightbloom.ingest.application.usecases.GetMessageUseCase;
import dev.rafex.insightbloom.ingest.application.usecases.IngestMessageUseCase;
import dev.rafex.insightbloom.ingest.domain.ports.UsersPort;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class IngestHandler extends BaseResourceHandler {

    private final IngestMessageUseCase ingestUseCase;
    private final GetMessageUseCase getMessageUseCase;
    private final UsersPort usersPort;
    private final DeleteConferenceDataUseCase deleteConferenceDataUseCase;

    public IngestHandler(final IngestMessageUseCase ingestUseCase, final GetMessageUseCase getMessageUseCase,
                         final UsersPort usersPort, final DeleteConferenceDataUseCase deleteConferenceDataUseCase) {
        this.ingestUseCase = ingestUseCase;
        this.getMessageUseCase = getMessageUseCase;
        this.usersPort = usersPort;
        this.deleteConferenceDataUseCase = deleteConferenceDataUseCase;
    }

    @Override
    protected String basePath() {
        return "/api/v1";
    }

    @Override
    protected List<Route> routes() {
        return List.of(
                Route.of("/messages", Set.of("POST")),
                Route.of("/messages/{id}", Set.of("GET")),
                Route.of("/webhooks/messages", Set.of("POST")),
                Route.of("/conferences/{conferenceId}/messages", Set.of("DELETE")));
    }

    @Override
    public Set<String> supportedMethods() {
        return Set.of("GET", "POST", "DELETE");
    }

    @Override
    public boolean delete(final HttpExchange x) {
        final var jx = asJetty(x);
        if (!validInternalAuth(jx)) { sendError(jx, 403, "forbidden", "Internal access only"); return true; }
        try {
            deleteConferenceDataUseCase.execute(jx.pathParam("conferenceId"));
            sendOk(jx, 200, Map.of("status", "deleted"));
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", "Internal error");
        }
        return true;
    }

    @Override
    public boolean get(final HttpExchange x) {
        final var jx = asJetty(x);
        final String id = jx.pathParam("id");
        if (id == null) {
            sendError(jx, 404, "not_found", "Endpoint not found");
            return true;
        }
        try {
            getMessageUseCase.execute(id).ifPresentOrElse(
                    m -> sendOk(jx, 200, m),
                    () -> sendError(jx, 404, "message_not_found", "Message not found"));
        } catch (final Exception e) {
            sendInternalError(jx, e);
        }
        return true;
    }

    @Override
    public boolean post(final HttpExchange x) {
        final var jx = asJetty(x);
        final boolean isWebhook = jx.path().contains("/webhooks/");
        return handleIngest(jx, isWebhook);
    }

    @SuppressWarnings("unchecked")
    private boolean handleIngest(final JettyHttpExchange jx, final boolean isWebhook) {
        try {
            final String authHeader = jx.request().getHeaders().get("Authorization");
            final String token = (authHeader != null && authHeader.startsWith("Bearer ")) ? authHeader.substring(7) : null;

            String authorUuid = "anonymous";
            String authorKind = "guest";
            String displayName = null;

            if (token != null) {
                final var validation = usersPort.validate(token);
                if (!validation.valid()) {
                    sendError(jx, 401, "token_invalid", "Invalid token");
                    return true;
                }
                authorUuid = validation.subjectUuid();
                authorKind = validation.kind();
            }

            final var body = parseBody(jx);
            final Map<Object, Object> author = body.get("author") instanceof Map ? (Map<Object, Object>) body.get("author") : new HashMap<>();
            final Map<Object, Object> device = body.get("device") instanceof Map ? (Map<Object, Object>) body.get("device") : new HashMap<>();
            final Map<Object, Object> msgMap = body.get("message") instanceof Map ? (Map<Object, Object>) body.get("message") : (Map<Object, Object>) (Map<?, ?>) body;

            final String conferenceUuid = (String) body.get("conferenceId");
            if (author.get("displayName") != null) displayName = (String) author.get("displayName");
            if (author.get("userId") != null) authorUuid = (String) author.get("userId");
            if (author.get("kind") != null) authorKind = (String) author.get("kind");

            final String fingerprint = device.get("fingerprint") != null ? (String) device.get("fingerprint") : "";
            final String msgType = msgMap.get("type") != null ? (String) msgMap.get("type") : "doubt";
            final String word = msgMap.get("word") != null ? (String) msgMap.get("word") : "";
            final String detail = msgMap.get("detail") != null ? (String) msgMap.get("detail") : "";
            final String receivedAt = body.get("receivedAt") != null ? (String) body.get("receivedAt") : Instant.now().toString();

            final var result = ingestUseCase.execute(new IngestMessageUseCase.IngestRequest(
                    conferenceUuid, authorUuid, authorKind, displayName, fingerprint,
                    msgType, isWebhook ? "WEBHOOK" : "REST", word, detail, receivedAt));

            sendOk(jx, 201, Map.of("messageId", result.getUuid(), "status", result.getWordStatus().name().toLowerCase()));
        } catch (final Exception e) {
            sendInternalError(jx, e);
        }
        return true;
    }
}
