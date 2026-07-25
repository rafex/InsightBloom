package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.AiProviderSettings;
import dev.rafex.insightbloom.users.domain.model.PlatformSettings;
import dev.rafex.insightbloom.users.domain.ports.PlatformSettingsRepository;

import java.net.URI;

/** Persists one independent AI capability from the IA dashboard. */
public class SetAiSettingsUseCase {
    private final PlatformSettingsRepository repository;

    public SetAiSettingsUseCase(final PlatformSettingsRepository repository) {
        this.repository = repository;
    }

    public PlatformSettings execute(final String capability, final ProviderUpdate update) {
        final PlatformSettings settings = repository.get();
        final AiProviderSettings target = provider(settings, capability);
        final boolean hadExplicitConfiguration = target.isConfigured();
        validate(update);
        target.setConfigured(true);
        target.setEnabled(update.enabled());
        target.setBaseUrl(trimTrailingSlash(update.baseUrl()));
        target.setModel(update.model().trim());
        if (update.clearApiKey()) target.setApiKey(null);
        else if (update.apiKey() != null && !update.apiKey().isBlank()) target.setApiKey(update.apiKey().trim());
        else if (!hadExplicitConfiguration) target.setApiKey(null);
        target.setSystemPrompt(update.systemPrompt() == null || update.systemPrompt().isBlank()
                ? null : update.systemPrompt().trim());
        target.setGuardrails(update.guardrails() == null || update.guardrails().isBlank()
                ? null : update.guardrails().trim());
        target.setTemperature(update.temperature());
        repository.save(settings);
        return settings;
    }

    public record ProviderUpdate(boolean enabled, String baseUrl, String model, String apiKey,
                                 boolean clearApiKey, String systemPrompt, String guardrails, Double temperature) { }

    private static void validate(final ProviderUpdate update) {
        if (update == null) throw new IllegalArgumentException("ai_provider_required");
        if (update.baseUrl() == null || update.baseUrl().isBlank()) {
            throw new IllegalArgumentException("ai_base_url_required");
        }
        try {
            final URI uri = URI.create(update.baseUrl().trim());
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null) throw new IllegalArgumentException();
        } catch (final IllegalArgumentException e) {
            throw new IllegalArgumentException("ai_base_url_invalid");
        }
        if (update.model() == null || update.model().isBlank()) {
            throw new IllegalArgumentException("ai_model_required");
        }
        if (update.temperature() != null && (update.temperature() < 0.0 || update.temperature() > 2.0)) {
            throw new IllegalArgumentException("ai_temperature_out_of_range");
        }
    }

    private static AiProviderSettings provider(final PlatformSettings settings, final String capability) {
        return switch (normalize(capability)) {
            case "chat" -> settings.getChatAi();
            case "tutor" -> settings.getTutorAi();
            case "survey" -> settings.getSurveyAi();
            case "seat-layout" -> settings.getSeatLayoutAi();
            default -> throw new IllegalArgumentException("ai_capability_invalid");
        };
    }

    private static String normalize(final String capability) {
        return capability == null ? "" : capability.trim().toLowerCase().replace('_', '-');
    }

    private static String trimTrailingSlash(final String value) {
        String result = value.trim();
        while (result.endsWith("/")) result = result.substring(0, result.length() - 1);
        return result;
    }
}
