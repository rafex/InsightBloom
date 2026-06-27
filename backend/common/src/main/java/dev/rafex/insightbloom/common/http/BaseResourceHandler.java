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
import java.util.Map;
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

    @SuppressWarnings("unchecked")
    protected Map<String, Object> parseBody(final JettyHttpExchange jx) throws IOException {
        return JSON_CODEC.readValue(Request.asInputStream(jx.request()), Map.class);
    }

    protected static JettyHttpExchange asJetty(final HttpExchange x) {
        return (JettyHttpExchange) x;
    }

    /** Shared check for service-to-service /internal/* endpoints. Fails open if INTERNAL_API_KEY is unset (dev mode). */
    protected static boolean validInternalAuth(final JettyHttpExchange jx) {
        final String key = System.getenv("INTERNAL_API_KEY");
        if (key == null || key.isEmpty()) {
            return true;
        }
        final String header = jx.request().getHeaders().get("X-Internal-Auth");
        return key.equals(header);
    }
}
