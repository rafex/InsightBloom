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
import dev.rafex.insightbloom.users.application.usecases.GetUserProfileUseCase;
import dev.rafex.insightbloom.users.application.usecases.UpdateProfileUseCase;
import dev.rafex.insightbloom.users.application.usecases.ValidateTokenUseCase;
import org.eclipse.jetty.server.Request;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class UserProfileHandler extends NonBlockingResourceHandler {

    private static final JsonCodec JSON_CODEC = JsonUtils.codec();
    private static final JettyApiResponses RESPONSES = new JettyApiResponses(JSON_CODEC);

    private final GetUserProfileUseCase getUserProfileUseCase;
    private final UpdateProfileUseCase updateProfileUseCase;
    private final ValidateTokenUseCase validateTokenUseCase;

    public UserProfileHandler(final GetUserProfileUseCase getUserProfileUseCase,
                              final UpdateProfileUseCase updateProfileUseCase,
                              final ValidateTokenUseCase validateTokenUseCase) {
        super(JSON_CODEC);
        this.getUserProfileUseCase = getUserProfileUseCase;
        this.updateProfileUseCase = updateProfileUseCase;
        this.validateTokenUseCase = validateTokenUseCase;
    }

    @Override
    protected String basePath() {
        return "/api/v1/users";
    }

    @Override
    protected List<Route> routes() {
        return List.of(Route.of("/{uuid}", Set.of("GET", "PUT")));
    }

    @Override
    public Set<String> supportedMethods() {
        return Set.of("GET", "PUT");
    }

    @Override
    public boolean get(final HttpExchange x) {
        final var jx = asJetty(x);
        try {
            final var profile = getUserProfileUseCase.execute(jx.pathParam("uuid"));
            if (profile.isPresent()) {
                RESPONSES.json(jx.response(), jx.callback(), 200,
                        new ApiResponse<>(profile.get(), ApiMeta.of(UUID.randomUUID().toString())));
            } else {
                RESPONSES.json(jx.response(), jx.callback(), 404,
                        ApiError.of("user_not_found", "User not found", UUID.randomUUID().toString()));
            }
        } catch (final Exception e) {
            RESPONSES.json(jx.response(), jx.callback(), 500,
                    ApiError.of("internal_error", e.getMessage(), UUID.randomUUID().toString()));
        }
        return true;
    }

    @Override
    public boolean put(final HttpExchange x) {
        final var jx = asJetty(x);
        final String uuid = jx.pathParam("uuid");
        final String token = extractToken(jx);
        if (token == null) { sendError(jx, 401, "token_missing", "Authorization required"); return true; }
        try {
            final var v = validateTokenUseCase.execute(token);
            if (!v.valid() || !v.subjectUuid().equals(uuid)) {
                sendError(jx, 403, "forbidden", "You can only edit your own profile");
                return true;
            }
            final var body = JSON_CODEC.readValue(Request.asInputStream(jx.request()), Map.class);
            final var result = updateProfileUseCase.execute(uuid, new UpdateProfileUseCase.Request(
                    (String) body.get("firstName"), (String) body.get("lastName")));
            if (result.isPresent()) {
                sendOk(jx, 200, result.get());
            } else {
                sendError(jx, 404, "user_not_found", "User not found");
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
