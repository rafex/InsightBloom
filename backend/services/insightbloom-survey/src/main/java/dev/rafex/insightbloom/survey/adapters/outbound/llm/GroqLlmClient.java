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
    private final String usersBaseUrl;
    private final String internalApiKey;
    private final JsonCodec jsonCodec;
    private final HttpClient httpClient;
    private volatile CachedSettings cachedSettings;

    public GroqLlmClient(final String usersBaseUrl, final String internalApiKey, final JsonCodec jsonCodec) {
        this.usersBaseUrl = usersBaseUrl;
        this.internalApiKey = internalApiKey;
        this.jsonCodec = jsonCodec;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    @Override
    public boolean isEnabled() {
        final ProviderSettings settings = loadSettings();
        return settings.enabled && settings.apiKey != null && !settings.apiKey.isBlank();
    }

    @Override
    public String complete(final String systemPrompt, final String userPrompt) {
        final ProviderSettings settings = loadSettings();
        if (!isEnabled()) {
            throw new IllegalStateException("llm_not_configured");
        }
        final Map<String, Object> body = Map.of(
                "model", settings.model,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)),
                "temperature", 0.3);

        final HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(settings.baseUrl + "/chat/completions"))
                .timeout(Duration.ofSeconds(60))
                .header("Authorization", "Bearer " + settings.apiKey)
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

    private ProviderSettings loadSettings() {
        final long now = System.currentTimeMillis();
        final CachedSettings cached = cachedSettings;
        if (cached != null && now - cached.loadedAt < 30_000) return cached.settings;
        try {
            final HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(usersBaseUrl + "/api/v1/settings/ai/internal"))
                    .timeout(Duration.ofSeconds(5)).GET();
            if (internalApiKey != null && !internalApiKey.isBlank()) {
                builder.header("X-Internal-Auth", internalApiKey);
            }
            final HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) throw new IllegalStateException("ai_settings_unavailable");
            final var data = jsonCodec.readTree(response.body()).path("data");
            final ProviderSettings settings = new ProviderSettings(
                    data.path("chatAiEnabled").asBoolean(false),
                    data.path("aiBaseUrl").asText(""), data.path("aiModel").asText(""),
                    data.path("aiApiKey").asText(null));
            cachedSettings = new CachedSettings(now, settings);
            return settings;
        } catch (final Exception e) {
            return new ProviderSettings(false, "", "", null);
        }
    }

    private record CachedSettings(long loadedAt, ProviderSettings settings) { }
    private record ProviderSettings(boolean enabled, String baseUrl, String model, String apiKey) { }
}
