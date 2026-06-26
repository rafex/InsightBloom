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
import dev.rafex.insightbloom.users.application.usecases.GetCertificateSettingsUseCase;
import dev.rafex.insightbloom.users.application.usecases.SaveCertificateSettingsUseCase;
import dev.rafex.insightbloom.users.application.usecases.ValidateTokenUseCase;
import org.eclipse.jetty.server.Request;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class CertificateSettingsHandler extends NonBlockingResourceHandler {

    private static final JsonCodec JSON_CODEC = JsonUtils.codec();
    private static final JettyApiResponses RESPONSES = new JettyApiResponses(JSON_CODEC);

    private final GetCertificateSettingsUseCase getUseCase;
    private final SaveCertificateSettingsUseCase saveUseCase;
    private final ValidateTokenUseCase validateTokenUseCase;

    public CertificateSettingsHandler(final GetCertificateSettingsUseCase getUseCase,
                                      final SaveCertificateSettingsUseCase saveUseCase,
                                      final ValidateTokenUseCase validateTokenUseCase) {
        super(JSON_CODEC);
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
        try {
            sendOk(jx, getUseCase.execute());
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
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
            if (!v.valid() || !"organizer".equals(v.role())) {
                sendError(jx, 403, "forbidden", "Only organizers can edit certificate settings");
                return true;
            }
            final var body = JSON_CODEC.readValue(Request.asInputStream(jx.request()), Map.class);
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

    private <T> void sendOk(final JettyHttpExchange jx, final T data) {
        RESPONSES.json(jx.response(), jx.callback(), 200,
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
