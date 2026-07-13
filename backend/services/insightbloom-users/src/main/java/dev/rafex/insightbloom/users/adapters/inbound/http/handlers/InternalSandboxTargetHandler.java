package dev.rafex.insightbloom.users.adapters.inbound.http.handlers;

import dev.rafex.ether.http.core.HttpExchange;
import dev.rafex.ether.http.core.Route;
import dev.rafex.ether.http.jetty12.exchange.JettyHttpExchange;
import dev.rafex.insightbloom.common.http.BaseResourceHandler;
import dev.rafex.insightbloom.users.application.usecases.ResolveSandboxTargetUseCase;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Llamado por insightbloom-toolsgateway (servicio-a-servicio, X-Internal-Auth) para resolver a
 * qué Pod de sandbox rutear una sesión del IDE — ver {@link ResolveSandboxTargetUseCase}.
 */
public class InternalSandboxTargetHandler extends BaseResourceHandler {
    private final ResolveSandboxTargetUseCase resolveSandboxTargetUseCase;

    public InternalSandboxTargetHandler(final ResolveSandboxTargetUseCase resolveSandboxTargetUseCase) {
        this.resolveSandboxTargetUseCase = resolveSandboxTargetUseCase;
    }

    @Override
    protected String basePath() {
        return "/internal/sandbox-target";
    }

    @Override
    protected List<Route> routes() {
        return List.of(Route.of("/", Set.of("GET")));
    }

    @Override
    public Set<String> supportedMethods() {
        return Set.of("GET");
    }

    @Override
    public boolean get(final HttpExchange x) {
        final var jx = asJetty(x);
        if (!validInternalAuth(jx)) {
            sendError(jx, 403, "forbidden", "Internal endpoint");
            return true;
        }
        final String token = queryParam(jx, "token");
        final String conferenceId = queryParam(jx, "conferenceId");
        if (token == null || token.isBlank() || conferenceId == null || conferenceId.isBlank()) {
            sendError(jx, 400, "invalid_request", "token and conferenceId are required");
            return true;
        }
        try {
            resolveSandboxTargetUseCase.execute(token, conferenceId)
                    .ifPresentOrElse(
                            target -> sendOk(jx, 200, Map.of("target", target)),
                            () -> sendError(jx, 404, "sandbox_not_found", "No active sandbox for this user/conference"));
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
        }
        return true;
    }
}
