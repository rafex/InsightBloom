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
import dev.rafex.insightbloom.users.application.usecases.CreateGuestUseCase;
import dev.rafex.insightbloom.users.application.usecases.LoginUseCase;
import dev.rafex.insightbloom.users.application.usecases.ValidateTokenUseCase;
import org.eclipse.jetty.server.Request;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class AuthHandler extends NonBlockingResourceHandler {

    private static final JsonCodec JSON_CODEC = JsonUtils.codec();
    private static final JettyApiResponses RESPONSES = new JettyApiResponses(JSON_CODEC);

    private final LoginUseCase loginUseCase;
    private final CreateGuestUseCase createGuestUseCase;
    private final ValidateTokenUseCase validateTokenUseCase;

    public AuthHandler(final LoginUseCase loginUseCase, final CreateGuestUseCase createGuestUseCase,
                       final ValidateTokenUseCase validateTokenUseCase) {
        super(JSON_CODEC);
        this.loginUseCase = loginUseCase;
        this.createGuestUseCase = createGuestUseCase;
        this.validateTokenUseCase = validateTokenUseCase;
    }

    @Override
    protected String basePath() {
        return "/api/v1/auth";
    }

    @Override
    protected List<Route> routes() {
        return List.of(
                Route.of("/login", Set.of("POST")),
                Route.of("/guest", Set.of("POST")),
                Route.of("/validate", Set.of("GET")));
    }

    @Override
    public Set<String> supportedMethods() {
        return Set.of("GET", "POST");
    }

    @Override
    public boolean post(final HttpExchange x) {
        final var jx = asJetty(x);
        final String path = jx.path();
        if (path.endsWith("/login")) return handleLogin(jx);
        if (path.endsWith("/guest")) return handleGuest(jx);
        sendError(jx, 404, "not_found", "Endpoint not found");
        return true;
    }

    @Override
    public boolean get(final HttpExchange x) {
        final var jx = asJetty(x);
        if (jx.path().endsWith("/validate")) return handleValidate(jx);
        sendError(jx, 404, "not_found", "Endpoint not found");
        return true;
    }

    private boolean handleLogin(final JettyHttpExchange jx) {
        try {
            final var body = JSON_CODEC.readValue(Request.asInputStream(jx.request()), Map.class);
            final String username = (String) body.get("username");
            final String password = (String) body.get("password");
            final var result = loginUseCase.execute(new LoginUseCase.LoginRequest(username, password));
            if (result.isPresent()) {
                sendOk(jx, 201, result.get());
            } else {
                sendError(jx, 401, "invalid_credentials", "Invalid username or password");
            }
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
        }
        return true;
    }

    private boolean handleGuest(final JettyHttpExchange jx) {
        try {
            final var body = JSON_CODEC.readValue(Request.asInputStream(jx.request()), Map.class);
            final var result = createGuestUseCase.execute(new CreateGuestUseCase.GuestRequest(
                    (String) body.get("displayName"),
                    (String) body.get("deviceFingerprint"),
                    (String) body.get("conferenceUuid")));
            sendOk(jx, 201, result);
        } catch (final IllegalArgumentException e) {
            sendError(jx, 404, e.getMessage(), "Conference not found");
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
        }
        return true;
    }

    private boolean handleValidate(final JettyHttpExchange jx) {
        final String auth = jx.request().getHeaders().get("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            sendError(jx, 401, "token_missing", "Authorization header missing");
            return true;
        }
        try {
            final var result = validateTokenUseCase.execute(auth.substring(7));
            if (result.valid()) {
                sendOk(jx, 200, result);
            } else {
                sendError(jx, 401, "token_invalid", "Token is invalid or expired");
            }
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
        }
        return true;
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
