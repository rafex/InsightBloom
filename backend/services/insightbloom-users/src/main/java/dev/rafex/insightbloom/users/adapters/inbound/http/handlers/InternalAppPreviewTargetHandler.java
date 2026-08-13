package dev.rafex.insightbloom.users.adapters.inbound.http.handlers;

import dev.rafex.ether.http.core.HttpExchange;
import dev.rafex.ether.http.core.Route;
import dev.rafex.insightbloom.common.http.BaseResourceHandler;
import dev.rafex.insightbloom.users.application.usecases.ResolveAppPreviewTargetUseCase;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Llamado por insightbloom-tools-gateway (servicio-a-servicio, X-Internal-Auth) para resolver a
 * qué Pod de sandbox rutear una request de app-preview -- ver
 * {@link ResolveAppPreviewTargetUseCase}. Mismo rol que {@link InternalSandboxTargetHandler}
 * para IDE Web/CLI, pero la autenticación acá es el {@code accessToken} de la publicación, no una
 * sesión de InsightBloom.
 */
public class InternalAppPreviewTargetHandler extends BaseResourceHandler {
    private final ResolveAppPreviewTargetUseCase resolveAppPreviewTargetUseCase;

    public InternalAppPreviewTargetHandler(final ResolveAppPreviewTargetUseCase resolveAppPreviewTargetUseCase) {
        this.resolveAppPreviewTargetUseCase = resolveAppPreviewTargetUseCase;
    }

    @Override
    protected String basePath() {
        return "/internal/app-preview-target";
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
        final String publicationId = queryParam(jx, "publicationId");
        final String accessToken = queryParam(jx, "token");
        if (publicationId == null || publicationId.isBlank() || accessToken == null || accessToken.isBlank()) {
            sendError(jx, 400, "invalid_request", "publicationId and token are required");
            return true;
        }
        try {
            resolveAppPreviewTargetUseCase.execute(publicationId, accessToken)
                    .ifPresentOrElse(
                            target -> sendOk(jx, 200, Map.of("target", target)),
                            () -> sendError(jx, 404, "app_preview_not_found",
                                    "No active app-preview for this publicationId/token"));
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", "Internal server error");
        }
        return true;
    }
}
