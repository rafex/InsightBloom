package dev.rafex.insightbloom.users.adapters.inbound.http.handlers;

import dev.rafex.ether.http.core.HttpExchange;
import dev.rafex.ether.http.core.Route;
import dev.rafex.ether.http.jetty12.exchange.JettyHttpExchange;
import dev.rafex.insightbloom.common.http.BaseResourceHandler;
import dev.rafex.insightbloom.users.application.usecases.GetCertificateSettingsUseCase;
import dev.rafex.insightbloom.users.application.usecases.SaveCertificateSettingsUseCase;
import dev.rafex.insightbloom.users.application.usecases.ValidateTokenUseCase;

import java.util.List;
import java.util.Set;

public class CertificateSettingsHandler extends BaseResourceHandler {

    private final GetCertificateSettingsUseCase getUseCase;
    private final SaveCertificateSettingsUseCase saveUseCase;
    private final ValidateTokenUseCase validateTokenUseCase;

    public CertificateSettingsHandler(final GetCertificateSettingsUseCase getUseCase,
                                      final SaveCertificateSettingsUseCase saveUseCase,
                                      final ValidateTokenUseCase validateTokenUseCase) {
        this.getUseCase = getUseCase;
        this.saveUseCase = saveUseCase;
        this.validateTokenUseCase = validateTokenUseCase;
    }

    @Override
    protected String basePath() {
        return "/api/v1/certificate-settings";
    }

    @Override
    protected List<Route> routes() {
        return List.of(Route.of("/", Set.of("GET", "PUT")));
    }

    @Override
    public Set<String> supportedMethods() {
        return Set.of("GET", "PUT");
    }

    @Override
    public boolean get(final HttpExchange x) {
        final var jx = asJetty(x);
        final String token = extractToken(jx);
        if (token == null) { sendError(jx, 401, "token_missing", "Authorization required"); return true; }
        try {
            final var v = validateTokenUseCase.execute(token);
            if (!v.valid() || !isAdmin(v.role())) {
                sendError(jx, 403, "forbidden", "Only platform admins can view certificate settings");
                return true;
            }
            sendOk(jx, getUseCase.execute());
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", "Could not load certificate settings");
        }
        return true;
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean put(final HttpExchange x) {
        final var jx = asJetty(x);
        final String token = extractToken(jx);
        if (token == null) { sendError(jx, 401, "token_missing", "Authorization required"); return true; }
        try {
            final var v = validateTokenUseCase.execute(token);
            if (!v.valid() || !isAdmin(v.role())) {
                sendError(jx, 403, "forbidden", "Only platform admins can edit certificate settings");
                return true;
            }
            final var body = parseBody(jx);
            final var result = saveUseCase.execute(new SaveCertificateSettingsUseCase.Request(
                    (String) body.get("logoBase64"),
                    (String) body.get("fontFamily"),
                    body.get("titleFontSize") == null ? null : ((Number) body.get("titleFontSize")).intValue(),
                    body.get("bodyFontSize") == null ? null : ((Number) body.get("bodyFontSize")).intValue(),
                    (String) body.get("primaryColorHex"),
                    (Boolean) body.get("showVenue"),
                    (Boolean) body.get("showSchedule"),
                    (Boolean) body.get("showIssuedDate")));
            sendOk(jx, result);
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
        }
        return true;
    }

    private String extractToken(final JettyHttpExchange jx) {
        final String auth = jx.request().getHeaders().get("Authorization");
        return (auth != null && auth.startsWith("Bearer ")) ? auth.substring(7) : null;
    }

    private static boolean isAdmin(final String role) {
        return legacyRoleHasAny(role, "admin");
    }
}
