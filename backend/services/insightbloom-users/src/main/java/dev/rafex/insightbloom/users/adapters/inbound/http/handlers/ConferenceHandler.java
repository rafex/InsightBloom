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
import dev.rafex.insightbloom.users.application.usecases.CreateConferenceUseCase;
import dev.rafex.insightbloom.users.application.usecases.GetConferenceHistoryUseCase;
import dev.rafex.insightbloom.users.application.usecases.GetConferenceUseCase;
import dev.rafex.insightbloom.users.application.usecases.JoinConferenceUseCase;
import dev.rafex.insightbloom.users.application.usecases.ValidateTokenUseCase;
import org.eclipse.jetty.server.Request;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class ConferenceHandler extends NonBlockingResourceHandler {

    private static final JsonCodec JSON_CODEC = JsonUtils.codec();
    private static final JettyApiResponses RESPONSES = new JettyApiResponses(JSON_CODEC);

    private final CreateConferenceUseCase createConferenceUseCase;
    private final GetConferenceUseCase getConferenceUseCase;
    private final ValidateTokenUseCase validateTokenUseCase;
    private final JoinConferenceUseCase joinConferenceUseCase;
    private final GetConferenceHistoryUseCase getConferenceHistoryUseCase;

    public ConferenceHandler(final CreateConferenceUseCase createConferenceUseCase,
                             final GetConferenceUseCase getConferenceUseCase,
                             final ValidateTokenUseCase validateTokenUseCase,
                             final JoinConferenceUseCase joinConferenceUseCase,
                             final GetConferenceHistoryUseCase getConferenceHistoryUseCase) {
        super(JSON_CODEC);
        this.createConferenceUseCase = createConferenceUseCase;
        this.getConferenceUseCase = getConferenceUseCase;
        this.validateTokenUseCase = validateTokenUseCase;
        this.joinConferenceUseCase = joinConferenceUseCase;
        this.getConferenceHistoryUseCase = getConferenceHistoryUseCase;
    }

    @Override
    protected String basePath() {
        return "/api/v1/conferences";
    }

    @Override
    protected List<Route> routes() {
        return List.of(
                Route.of("/", Set.of("GET", "POST")),
                Route.of("/by-friendly/{friendlyId}", Set.of("GET")),
                Route.of("/by-short/{shortCode}", Set.of("GET")),
                Route.of("/join", Set.of("POST")),
                Route.of("/history", Set.of("GET")),
                Route.of("/{id}", Set.of("GET", "DELETE")));
    }

    @Override
    public Set<String> supportedMethods() {
        return Set.of("GET", "POST", "DELETE");
    }

    @Override
    public boolean get(final HttpExchange x) {
        final var jx = asJetty(x);
        final String path = jx.path();
        if (path.contains("/by-friendly/")) {
            return handleGetByFriendly(jx, jx.pathParam("friendlyId"));
        }
        if (path.contains("/by-short/")) {
            return handleGetByShortCode(jx, jx.pathParam("shortCode"));
        }
        if (path.endsWith("/history")) {
            return handleHistory(jx);
        }
        final String id = jx.pathParam("id");
        if (id != null) {
            return handleGetById(jx, id);
        }
        return handleList(jx);
    }

    @Override
    public boolean post(final HttpExchange x) {
        final var jx = asJetty(x);
        if (jx.path().endsWith("/join")) {
            return handleJoin(jx);
        }
        return handleCreate(jx);
    }

    @Override
    public boolean delete(final HttpExchange x) {
        final var jx = asJetty(x);
        return handleDelete(jx, jx.pathParam("id"));
    }

    private boolean handleList(final JettyHttpExchange jx) {
        final String token = extractToken(jx);
        if (token == null) { sendError(jx, 401, "token_missing", "Authorization required"); return true; }
        try {
            final var v = validateTokenUseCase.execute(token);
            if (!v.valid()) { sendError(jx, 401, "token_invalid", "Invalid token"); return true; }
            sendOk(jx, 200, getConferenceUseCase.byUser(v.subjectUuid()));
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
        }
        return true;
    }

    private boolean handleCreate(final JettyHttpExchange jx) {
        final String token = extractToken(jx);
        if (token == null) { sendError(jx, 401, "token_missing", "Authorization required"); return true; }
        try {
            final var v = validateTokenUseCase.execute(token);
            if (!v.valid() || !"organizer".equals(v.role())) {
                sendError(jx, 403, "forbidden", "Only organizers can create conferences");
                return true;
            }
            final var body = JSON_CODEC.readValue(Request.asInputStream(jx.request()), Map.class);
            final Double latitude = body.get("latitude") instanceof Number n ? n.doubleValue() : null;
            final Double longitude = body.get("longitude") instanceof Number n ? n.doubleValue() : null;
            final var result = createConferenceUseCase.execute(new CreateConferenceUseCase.CreateRequest(
                    (String) body.get("name"), v.subjectUuid(), (String) body.get("expiresAt"),
                    latitude, longitude));
            sendOk(jx, 201, result);
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
        }
        return true;
    }

    private boolean handleGetById(final JettyHttpExchange jx, final String id) {
        try {
            getConferenceUseCase.byId(id).ifPresentOrElse(
                    c -> sendOk(jx, 200, c),
                    () -> sendError(jx, 404, "conference_not_found", "Conference not found"));
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
        }
        return true;
    }

    private boolean handleGetByFriendly(final JettyHttpExchange jx, final String friendlyId) {
        try {
            getConferenceUseCase.byFriendlyId(friendlyId).ifPresentOrElse(
                    c -> sendOk(jx, 200, c),
                    () -> sendError(jx, 404, "conference_not_found", "Conference not found"));
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
        }
        return true;
    }

    private boolean handleGetByShortCode(final JettyHttpExchange jx, final String shortCode) {
        try {
            getConferenceUseCase.byShortCode(shortCode).ifPresentOrElse(
                    c -> sendOk(jx, 200, c),
                    () -> sendError(jx, 404, "conference_not_found", "Conference not found"));
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
        }
        return true;
    }

    private boolean handleJoin(final JettyHttpExchange jx) {
        final String token = extractToken(jx);
        if (token == null) { sendError(jx, 401, "token_missing", "Authorization required"); return true; }
        try {
            final var v = validateTokenUseCase.execute(token);
            if (!v.valid()) { sendError(jx, 401, "token_invalid", "Invalid token"); return true; }
            final var body = JSON_CODEC.readValue(Request.asInputStream(jx.request()), Map.class);
            final String identifier = (String) body.get("identifier");
            final var conference = joinConferenceUseCase.execute(v.subjectUuid(), identifier);
            sendOk(jx, 200, conference);
        } catch (final IllegalArgumentException e) {
            sendError(jx, 404, e.getMessage(), "Esta conferencia ya no se encuentra disponible");
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
        }
        return true;
    }

    private boolean handleHistory(final JettyHttpExchange jx) {
        final String token = extractToken(jx);
        if (token == null) { sendError(jx, 401, "token_missing", "Authorization required"); return true; }
        try {
            final var v = validateTokenUseCase.execute(token);
            if (!v.valid()) { sendError(jx, 401, "token_invalid", "Invalid token"); return true; }
            sendOk(jx, 200, getConferenceHistoryUseCase.execute(v.subjectUuid()));
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
        }
        return true;
    }

    private boolean handleDelete(final JettyHttpExchange jx, final String id) {
        final String token = extractToken(jx);
        if (token == null) { sendError(jx, 401, "token_missing", "Authorization required"); return true; }
        try {
            final var v = validateTokenUseCase.execute(token);
            if (!v.valid()) { sendError(jx, 401, "token_invalid", "Invalid token"); return true; }
            final boolean deleted = getConferenceUseCase.delete(id, v.subjectUuid());
            if (deleted) {
                sendOk(jx, 200, Map.of("deleted", true));
            } else {
                sendError(jx, 404, "not_found", "Conference not found or not owned by you");
            }
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
        }
        return true;
    }

    private String extractToken(final JettyHttpExchange jx) {
        final String auth = jx.request().getHeaders().get("Authorization");
        return (auth != null && auth.startsWith("Bearer ")) ? auth.substring(7) : null;
    }

    private <T> void sendOk(final JettyHttpExchange jx, final int status, final T data) {
        RESPONSES.json(jx.response(), jx.callback(), status,
                new ApiResponse<>(data, ApiMeta.of(UUID.randomUUID().toString())));
    }

    private void sendError(final JettyHttpExchange jx, final int status, final String code, final String message) {
        RESPONSES.json(jx.response(), jx.callback(), status,
                ApiError.of(code, message, UUID.randomUUID().toString()));
    }

    private static JettyHttpExchange asJetty(final HttpExchange x) {
        return (JettyHttpExchange) x;
    }
}
