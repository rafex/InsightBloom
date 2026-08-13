package dev.rafex.insightbloom.common.http;

import dev.rafex.ether.http.core.HttpExchange;
import dev.rafex.ether.http.jetty12.exchange.JettyHttpExchange;
import dev.rafex.ether.http.jetty12.handler.NonBlockingResourceHandler;
import dev.rafex.ether.http.jetty12.response.JettyApiResponses;
import dev.rafex.ether.json.JsonCodec;
import dev.rafex.ether.json.JsonUtils;
import dev.rafex.insightbloom.contracts.ApiError;
import dev.rafex.insightbloom.contracts.ApiMeta;
import dev.rafex.insightbloom.contracts.ApiResponse;
import org.eclipse.jetty.server.Request;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public abstract class BaseResourceHandler extends NonBlockingResourceHandler {

    private static final JettyApiResponses RESPONSES;
    private static final JsonCodec JSON_CODEC;

    static {
        JSON_CODEC = JsonUtils.codec();
        RESPONSES = new JettyApiResponses(JSON_CODEC);
    }

    protected BaseResourceHandler() {
        super(JSON_CODEC);
    }

    protected <T> void sendOk(final JettyHttpExchange jx, final int status, final T data) {
        RESPONSES.json(jx.response(), jx.callback(), status,
                new ApiResponse<>(data, ApiMeta.of(UUID.randomUUID().toString())));
    }

    protected <T> void sendOk(final JettyHttpExchange jx, final int status, final T data, final ApiMeta meta) {
        RESPONSES.json(jx.response(), jx.callback(), status,
                new ApiResponse<>(data, meta));
    }

    protected <T> void sendOk(final JettyHttpExchange jx, final T data) {
        sendOk(jx, 200, data);
    }

    protected void sendError(final JettyHttpExchange jx, final int status, final String code, final String message) {
        RESPONSES.json(jx.response(), jx.callback(), status,
                ApiError.of(code, message, UUID.randomUUID().toString()));
    }

    protected void sendInternalError(final JettyHttpExchange jx, final Exception e) {
        System.err.println("Internal server error in " + this.getClass().getSimpleName() + ": " + e.getMessage());
        e.printStackTrace(System.err);
        sendError(jx, 500, "internal_error", "Internal server error");
    }

    @SuppressWarnings("unchecked")
    protected Map<String, Object> parseBody(final JettyHttpExchange jx) throws IOException {
        return JSON_CODEC.readValue(Request.asInputStream(jx.request()), Map.class);
    }

    protected static JettyHttpExchange asJetty(final HttpExchange x) {
        return (JettyHttpExchange) x;
    }

    /** Shared check for service-to-service /internal/* endpoints. Fails closed when INTERNAL_API_KEY is unset. */
    protected static boolean validInternalAuth(final JettyHttpExchange jx) {
        final String key = System.getenv("INTERNAL_API_KEY");
        if (key == null || key.isEmpty()) {
            System.err.println("SECURITY WARNING: INTERNAL_API_KEY is not set — rejecting internal request");
            return false;
        }
        final String header = jx.request().getHeaders().get("X-Internal-Auth");
        return header != null && MessageDigest.isEqual(
                key.getBytes(StandardCharsets.UTF_8), header.getBytes(StandardCharsets.UTF_8));
    }

    /** Match legacy CSV roles as complete tokens, never with substring authorization. */
    protected static boolean legacyRoleHasAny(final String legacyRoles, final String... expectedRoles) {
        if (legacyRoles == null || legacyRoles.isBlank() || expectedRoles == null) return false;
        final Set<String> expected = Arrays.stream(expectedRoles)
                .filter(java.util.Objects::nonNull)
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toSet());
        return Arrays.stream(legacyRoles.split("[,\\s]+"))
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .anyMatch(expected::contains);
    }
}
