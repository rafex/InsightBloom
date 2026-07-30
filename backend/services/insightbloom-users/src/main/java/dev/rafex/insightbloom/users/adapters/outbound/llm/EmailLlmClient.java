package dev.rafex.insightbloom.users.adapters.outbound.llm;

import dev.rafex.ether.json.JsonCodec;
import dev.rafex.insightbloom.users.domain.model.PlatformSettings;
import dev.rafex.insightbloom.users.domain.ports.PlatformSettingsRepository;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

public class EmailLlmClient {
    private final PlatformSettingsRepository settingsRepository;
    private final JsonCodec jsonCodec;
    private final HttpClient httpClient;

    public EmailLlmClient(final PlatformSettingsRepository settingsRepository, final JsonCodec jsonCodec) {
        this.settingsRepository = settingsRepository;
        this.jsonCodec = jsonCodec;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    public boolean isEnabled() {
        final PlatformSettings settings = settingsRepository.get();
        final var provider = settings.getEmailAi();
        return provider.isEnabled() && provider.getApiKey() != null && !provider.getApiKey().isBlank();
    }

    public String complete(final String userPrompt) {
        final PlatformSettings settings = settingsRepository.get();
        final var provider = settings.getEmailAi();
        if (!isEnabled()) {
            throw new IllegalStateException("email_ai_not_configured");
        }
        final String systemPrompt = combinePrompts(provider.getSystemPrompt(), provider.getGuardrails(),
                "Sos un asistente de redaccion profesional para comunicaciones de eventos. "
                        + "El organizador te describe que quiere comunicar y vos generas un borrador de mensaje. "
                        + "El mensaje debe ser conciso, profesional y apto para email. "
                        + "Responde UNICAMENTE con el texto del mensaje en HTML, sin explicaciones adicionales. "
                        + "Usa etiquetas HTML semanticas: <p>, <strong>, <em>, <ul>, <ol>, <li>, <h3>, <br>. "
                        + "No uses <html>, <head> ni <body>.");

        final Map<String, Object> body = Map.of(
                "model", provider.getModel(),
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)),
                "temperature", provider.getTemperature() == null ? 0.7 : provider.getTemperature(),
                "max_tokens", 800);

        final HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(provider.getBaseUrl() + "/chat/completions"))
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", "Bearer " + provider.getApiKey())
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

    private static String combinePrompts(final String basePrompt, final String guardrails,
                                         final String operationPrompt) {
        final StringBuilder result = new StringBuilder();
        if (basePrompt != null && !basePrompt.isBlank()) result.append(basePrompt);
        if (guardrails != null && !guardrails.isBlank()) {
            if (result.length() > 0) result.append("\n\n");
            result.append(guardrails);
        }
        if (result.length() > 0) result.append("\n\n");
        result.append(operationPrompt);
        return result.toString();
    }
}
