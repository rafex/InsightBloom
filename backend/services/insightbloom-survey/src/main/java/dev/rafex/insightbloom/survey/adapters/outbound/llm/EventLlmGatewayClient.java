package dev.rafex.insightbloom.survey.adapters.outbound.llm;

import dev.rafex.ether.json.JsonCodec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Survey service client. It has no credential management methods and never receives provider keys. */
public final class EventLlmGatewayClient {
    private final String baseUrl = trim(System.getenv("LLM_GATEWAY_INTERNAL_URL"));
    private final String serviceToken = System.getenv().getOrDefault("LLM_GATEWAY_SURVEY_TOKEN", "");
    private final String signingKey = System.getenv().getOrDefault("LLM_GATEWAY_CONTEXT_SIGNING_KEY", "");
    private final JsonCodec codec;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public EventLlmGatewayClient(final JsonCodec codec) { this.codec = codec; }

    public boolean isConfigured(final String eventId, final String capability) {
        final var node = request("GET", "/internal/credentials/event/" + segment(eventId) + "/" + segment(capability), null);
        return codec.at(node, "/data/configured").asBoolean(false);
    }

    public String complete(final String eventId, final String capability, final String systemPrompt,
                           final String userPrompt) {
        segment(eventId);
        segment(capability);
        final Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("organizerId", eventId);
        payload.put("eventId", eventId);
        payload.put("capability", capability);
        payload.put("contextToken", contextToken(eventId, capability));
        payload.put("messages", List.of(Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)));
        final var node = request("POST", "/internal/complete", payload);
        final var content = codec.at(node, "/data/choices/0/message/content");
        if (!content.isTextual()) throw new IllegalStateException("llm_provider_response_invalid");
        return content.asText();
    }

    private com.fasterxml.jackson.databind.JsonNode request(final String method, final String path,
                                                             final Map<String, Object> body) {
        if (baseUrl.isBlank() || serviceToken.isBlank()) throw new IllegalStateException("llm_gateway_unavailable");
        try {
            final var builder = HttpRequest.newBuilder().uri(URI.create(baseUrl + path)).timeout(Duration.ofSeconds(35))
                    .header("X-Service-Id", "survey").header("X-Service-Token", serviceToken)
                    .header("Accept", "application/json");
            if (body == null) builder.GET();
            else builder.header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(codec.toJson(body)));
            final var response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                if (response.statusCode() == 404) throw new IllegalStateException("llm_not_configured");
                throw new IllegalStateException("llm_provider_unavailable");
            }
            return codec.readTree(response.body());
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("llm_provider_unavailable");
        } catch (final IllegalStateException e) { throw e;
        } catch (final Exception e) { throw new IllegalStateException("llm_provider_unavailable"); }
    }

    private String contextToken(final String eventId, final String capability) {
        if (signingKey.length() < 32) throw new IllegalStateException("llm_gateway_unavailable");
        try {
            final String claims = codec.toJson(Map.of("sub", eventId, "event", eventId,
                    "capabilities", List.of(capability), "exp", System.currentTimeMillis() / 1000 + 60));
            final String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(claims.getBytes(StandardCharsets.UTF_8));
            final Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return payload + "." + Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (final Exception e) { throw new IllegalStateException("llm_gateway_unavailable"); }
    }

    private static String trim(final String value) { return value == null ? "" : value.replaceAll("/+$", ""); }

    private static String segment(final String value) {
        if (value == null || !value.matches("[A-Za-z0-9_-]{1,128}")) {
            throw new IllegalArgumentException("llm_scope_invalid");
        }
        return value;
    }
}
