package dev.rafex.insightbloom.users.adapters.inbound.http.handlers;

import dev.rafex.ether.http.core.HttpExchange;
import dev.rafex.ether.http.core.Route;
import dev.rafex.insightbloom.common.http.BaseResourceHandler;
import dev.rafex.insightbloom.users.application.usecases.ResolveEgressPolicyUseCase;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Llamado por insightbloom-egress-proxy (servicio-a-servicio, X-Internal-Auth -- mismo mecanismo
 * que {@link InternalSandboxTargetHandler}, el proxy es un componente de plataforma sin acceso de
 * alumno, a diferencia del seat-agent) para resolver la política de egress efectiva de la IP de
 * origen de una conexión -- ver {@link ResolveEgressPolicyUseCase}.
 */
public class InternalEgressPolicyHandler extends BaseResourceHandler {
    private final ResolveEgressPolicyUseCase resolveEgressPolicyUseCase;

    public InternalEgressPolicyHandler(final ResolveEgressPolicyUseCase resolveEgressPolicyUseCase) {
        this.resolveEgressPolicyUseCase = resolveEgressPolicyUseCase;
    }

    @Override
    protected String basePath() {
        return "/internal/egress-policy";
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
        final String sourceIp = queryParam(jx, "sourceIp");
        if (sourceIp == null || sourceIp.isBlank()) {
            sendError(jx, 400, "invalid_request", "sourceIp is required");
            return true;
        }
        try {
            resolveEgressPolicyUseCase.execute(sourceIp)
                    .ifPresentOrElse(
                            resolution -> sendOk(jx, 200, Map.of(
                                    "conferenceUuid", resolution.conferenceUuid(),
                                    "internetEnabled", resolution.internetEnabled(),
                                    "allowed", resolution.allowed(),
                                    "blocked", resolution.blocked())),
                            () -> sendError(jx, 404, "unknown_source", "No sandbox found for this source IP"));
        } catch (final Exception e) {
            sendInternalError(jx, e);
        }
        return true;
    }
}
