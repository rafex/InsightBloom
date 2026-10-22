package dev.rafex.insightbloom.users.adapters.outbound.llm;

import dev.rafex.ether.json.JsonCodec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.LinkedHashMap;
import java.util.Map;

/** Internal client for event-scoped credential metadata and writes. Plain keys are sent only
 * over the private service connection and are never returned by this adapter. */
public final class LlmGatewayClient {
    private final String baseUrl;
    private final String serviceToken;
    private final String serviceId;
    private final String signingKey;
    private final JsonCodec codec;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public LlmGatewayClient(final String baseUrl, final String serviceToken, final JsonCodec codec) {
        this(baseUrl, "users", serviceToken, System.getenv("LLM_GATEWAY_CONTEXT_SIGNING_KEY"), codec);
    }

    public LlmGatewayClient(final String baseUrl, final String serviceToken, final String signingKey,
                            final JsonCodec codec) {
        this(baseUrl, "users", serviceToken, signingKey, codec);
    }

    public LlmGatewayClient(final String baseUrl, final String serviceId, final String serviceToken,
                            final String signingKey, final JsonCodec codec) {
        this.baseUrl = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
        this.serviceToken = serviceToken == null ? "" : serviceToken;
        this.serviceId = serviceId == null ? "users" : serviceId;
        this.signingKey = signingKey == null ? "" : signingKey;
        this.codec = codec;
    }

    public Map<String, Object> status(final String eventId, final String capability) {
        final var node = request("GET", "/internal/credentials/event/" + segment(eventId) + "/" + segment(capability), null, null);
        final var data = codec.at(node, "/data");
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("configured", codec.at(data, "/configured").asBoolean(false));
        if (codec.at(data, "/provider").isTextual()) result.put("provider", codec.at(data, "/provider").asText());
        if (codec.at(data, "/model").isTextual()) result.put("model", codec.at(data, "/model").asText());
        if (codec.at(data, "/baseUrl").isTextual()) result.put("baseUrl", codec.at(data, "/baseUrl").asText());
        if (codec.at(data, "/updatedAt").isNumber()) result.put("updatedAt", codec.at(data, "/updatedAt").asLong());
        return result;
    }

    public Map<String, Object> set(final String eventId, final String capability, final String provider,
                                   final String model, final String baseUrl, final String apiKey,
                                   final String actorId) {
        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("scopeType", "event");
        body.put("scopeId", eventId);
        body.put("capability", capability);
        body.put("provider", provider);
        body.put("model", model);
        body.put("apiKey", apiKey);
        if (baseUrl != null && !baseUrl.isBlank()) body.put("baseUrl", baseUrl);
        request("PUT", "/internal/credentials", body, actorId);
        return status(eventId, capability);
    }

    public void clear(final String eventId, final String capability, final String actorId) {
        request("DELETE", "/internal/credentials/event/" + segment(eventId) + "/" + segment(capability), null, actorId);
    }

    private com.fasterxml.jackson.databind.JsonNode request(final String method, final String path,
                                                            final Map<String, Object> body, final String actorId) {
        if (baseUrl.isBlank() || serviceToken.isBlank()) throw new IllegalStateException("llm_gateway_unavailable");
        try {
            final HttpRequest.Builder builder = HttpRequest.newBuilder().uri(URI.create(baseUrl + path))
                    .timeout(Duration.ofSeconds(10)).header("X-Service-Id", serviceId)
                    .header("X-Service-Token", serviceToken).header("Accept", "application/json");
            if (actorId != null) builder.header("X-Actor-Id", actorId);
            if (body == null) builder.method(method, HttpRequest.BodyPublishers.noBody());
            else builder.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(codec.toJson(body)));
            final var response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("llm_gateway_unavailable");
            }
            return codec.readTree(response.body());
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("llm_gateway_unavailable");
        } catch (final Exception e) {
            if (e instanceof IllegalStateException state && "llm_gateway_unavailable".equals(state.getMessage())) throw state;
            throw new IllegalStateException("llm_gateway_unavailable");
        }
    }

    private static String segment(final String value) {
        if (value == null || !value.matches("[A-Za-z0-9_-]{1,128}")) throw new IllegalArgumentException("llm_scope_invalid");
        return value;
    }

    public boolean isConfigured(final String eventId, final String capability) {
        return Boolean.TRUE.equals(status(eventId, capability).get("configured"));
    }

    public String complete(final String eventId, final String capability, final String systemPrompt,
                           final String userPrompt, final Double temperature) {
        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("organizerId", eventId);
        body.put("eventId", eventId);
        body.put("capability", capability);
        body.put("contextToken", contextToken(eventId, capability));
        body.put("messages", java.util.List.of(Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)));
        if (temperature != null) body.put("temperature", temperature);
        final var response = request("POST", "/internal/complete", body, null);
        final var content = codec.at(response, "/data/choices/0/message/content");
        if (!content.isTextual()) throw new IllegalStateException("llm_provider_response_invalid");
        return content.asText();
    }

    public String contextTokenForEvent(final String eventId, final String capability) {
        if (signingKey.length() < 32) throw new IllegalStateException("llm_gateway_unavailable");
        try {
            final String claims = codec.toJson(Map.of("sub", eventId, "event", eventId,
                    "capabilities", java.util.List.of(capability), "exp", System.currentTimeMillis() / 1000 + 60));
            final String payload = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(claims.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            final Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingKey.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
            final String signature = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            return payload + "." + signature;
        } catch (final Exception e) {
            throw new IllegalStateException("llm_gateway_unavailable");
        }
    }

    private String contextToken(final String eventId, final String capability) {
        return contextTokenForEvent(eventId, capability);
    }
}
