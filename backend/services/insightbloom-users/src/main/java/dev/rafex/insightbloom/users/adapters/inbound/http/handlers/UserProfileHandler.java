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

import java.util.List;
import java.util.Set;
import java.util.UUID;

public class UserProfileHandler extends NonBlockingResourceHandler {

    private static final JsonCodec JSON_CODEC = JsonUtils.codec();
    private static final JettyApiResponses RESPONSES = new JettyApiResponses(JSON_CODEC);

    private final GetUserProfileUseCase getUserProfileUseCase;

    public UserProfileHandler(final GetUserProfileUseCase getUserProfileUseCase) {
        super(JSON_CODEC);
        this.getUserProfileUseCase = getUserProfileUseCase;
    }

    @Override
    protected String basePath() {
        return "/api/v1/users";
    }

    @Override
    protected List<Route> routes() {
        return List.of(Route.of("/{uuid}", Set.of("GET")));
    }

    @Override
    public Set<String> supportedMethods() {
        return Set.of("GET");
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

    private static JettyHttpExchange asJetty(final HttpExchange x) {
        return (JettyHttpExchange) x;
    }
}
