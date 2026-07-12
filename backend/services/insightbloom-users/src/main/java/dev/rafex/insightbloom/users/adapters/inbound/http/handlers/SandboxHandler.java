package dev.rafex.insightbloom.users.adapters.inbound.http.handlers;

import dev.rafex.ether.http.core.HttpExchange;
import dev.rafex.ether.http.core.Route;
import dev.rafex.ether.http.jetty12.exchange.JettyHttpExchange;
import dev.rafex.insightbloom.common.http.BaseResourceHandler;
import dev.rafex.insightbloom.users.application.usecases.AssignSandboxUseCase;
import dev.rafex.insightbloom.users.application.usecases.ValidateTokenUseCase;
import dev.rafex.insightbloom.users.domain.model.Sandbox;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class SandboxHandler extends BaseResourceHandler {
    private final AssignSandboxUseCase assignSandboxUseCase;
    private final ValidateTokenUseCase validateTokenUseCase;
    private final String gatewayBaseUrl; // ej. "https://ide-insightbloom.v1.rafex.cloud"

    public SandboxHandler(final AssignSandboxUseCase assignSandboxUseCase,
                         final ValidateTokenUseCase validateTokenUseCase,
                         final String gatewayBaseUrl) {
        this.assignSandboxUseCase = assignSandboxUseCase;
        this.validateTokenUseCase = validateTokenUseCase;
        this.gatewayBaseUrl = gatewayBaseUrl;
    }

    @Override
    public String basePath() {
        return "/api/v1/conferences";
    }

    @Override
    public List<Route> routes() {
        return List.of(
            Route.of("/{id}/sandbox", Set.of("GET"))
        );
    }

    @Override
    public Set<String> supportedMethods() {
        return Set.of("GET");
    }

    @Override
    public boolean get(final HttpExchange x) {
        final var jx = asJetty(x);
        if (jx.path().endsWith("/sandbox")) {
            return handleGetSandbox(jx, jx.pathParam("id"));
        }
        return false;
    }

    private boolean handleGetSandbox(final JettyHttpExchange jx, final String conferenceId) {
        final String token = extractToken(jx);
        if (token == null) {
            sendError(jx, 401, "token_missing", "Authorization required");
            return true;
        }
        try {
            final var v = validateTokenUseCase.execute(token);
            if (!v.valid()) {
                sendError(jx, 401, "token_invalid", "Invalid token");
                return true;
            }

            final Sandbox sandbox = assignSandboxUseCase.execute(conferenceId, v.subjectUuid());

            // Respuesta: info del sandbox + URL base del gateway
            final Map<String, Object> response = Map.of(
                "sandboxUuid", sandbox.getUuid(),
                "sandboxSlot", sandbox.getSandboxSlot(),
                "gatewayUrl", gatewayBaseUrl,
                "sandboxPath", "/"
            );
            sendOk(jx, 200, response);
            return true;
        } catch (final IllegalArgumentException e) {
            if ("conference_not_found".equals(e.getMessage())) {
                sendError(jx, 404, "conference_not_found", "Conference not found");
            } else if ("sandbox_pool_full".equals(e.getMessage())) {
                sendError(jx, 409, "sandbox_pool_full", "Sandbox pool is full");
            } else {
                sendError(jx, 400, "invalid_request", e.getMessage());
            }
            return true;
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", "Internal server error");
            return true;
        }
    }

    private String extractToken(final JettyHttpExchange jx) {
        final String auth = jx.request().getHeaders().get("Authorization");
        return (auth != null && auth.startsWith("Bearer ")) ? auth.substring(7) : null;
    }
}
