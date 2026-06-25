package dev.rafex.insightbloom.survey.adapters.outbound.llm;

import dev.rafex.ether.json.JsonCodec;
import dev.rafex.insightbloom.survey.domain.ports.LlmPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

public class GroqLlmClient implements LlmPort {
    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final JsonCodec jsonCodec;
    private final HttpClient httpClient;

    public GroqLlmClient(final String baseUrl, final String apiKey, final String model, final JsonCodec jsonCodec) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.model = model;
        this.jsonCodec = jsonCodec;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    @Override
    public boolean isEnabled() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public String complete(final String systemPrompt, final String userPrompt) {
        if (!isEnabled()) {
            throw new IllegalStateException("llm_not_configured");
        }
        final Map<String, Object> body = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)),
                "temperature", 0.3);

        final HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/chat/completions"))
                .timeout(Duration.ofSeconds(60))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonCodec.toJson(body)))
                .build();

        try {
            final HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                throw new RuntimeException("llm_request_failed: " + response.statusCode() + " " + response.body());
            }
            final var node = jsonCodec.readTree(response.body());
            return jsonCodec.at(node, "/choices/0/message/content").asText();
        } catch (final java.io.IOException | InterruptedException e) {
            throw new RuntimeException("llm_request_failed", e);
        }
    }
}
