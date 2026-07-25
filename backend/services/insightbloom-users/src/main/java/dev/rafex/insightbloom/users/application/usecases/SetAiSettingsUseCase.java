package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.PlatformSettings;
import dev.rafex.insightbloom.users.domain.ports.PlatformSettingsRepository;

import java.net.URI;

/** Persists the provider configuration managed from the IA dashboard. */
public class SetAiSettingsUseCase {
    private final PlatformSettingsRepository repository;

    public SetAiSettingsUseCase(final PlatformSettingsRepository repository) {
        this.repository = repository;
    }

    public PlatformSettings execute(final boolean enabled, final String baseUrl, final String model,
                                    final String apiKey, final boolean clearApiKey,
                                    final String systemPrompt, final Double temperature) {
        if (baseUrl == null || baseUrl.isBlank()) throw new IllegalArgumentException("ai_base_url_required");
        try {
            final URI uri = URI.create(baseUrl.trim());
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null) {
                throw new IllegalArgumentException("ai_base_url_invalid");
            }
        } catch (final IllegalArgumentException e) {
            throw new IllegalArgumentException("ai_base_url_invalid");
        }
        if (model == null || model.isBlank()) throw new IllegalArgumentException("ai_model_required");
        if (temperature != null && (temperature < 0.0 || temperature > 2.0)) {
            throw new IllegalArgumentException("chat_temperature_out_of_range");
        }

        final PlatformSettings settings = repository.get();
        settings.setChatAiEnabled(enabled);
        settings.setAiBaseUrl(trimTrailingSlash(baseUrl));
        settings.setAiModel(model.trim());
        if (clearApiKey) settings.setAiApiKey(null);
        else if (apiKey != null && !apiKey.isBlank()) settings.setAiApiKey(apiKey.trim());
        settings.setChatSystemPrompt(systemPrompt == null || systemPrompt.isBlank() ? null : systemPrompt);
        settings.setChatTemperature(temperature);
        repository.save(settings);
        return settings;
    }

    private static String trimTrailingSlash(final String value) {
        String result = value.trim();
        while (result.endsWith("/")) result = result.substring(0, result.length() - 1);
        return result;
    }
}
