package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.PlatformSettings;
import dev.rafex.insightbloom.users.domain.ports.PlatformSettingsRepository;

/**
 * Permite al panel administrativo controlar el prompt de sistema y la temperatura del bot de
 * chat (Roberto) sin depender de un redeploy ni de variables de entorno de GitHub Actions
 * (el servicio usa un prompt pedagógico incorporado si estos campos son null).
 */
public class SetChatSettingsUseCase {
    private static final double MIN_TEMPERATURE = 0.0;
    private static final double MAX_TEMPERATURE = 2.0;

    private final PlatformSettingsRepository repository;

    public SetChatSettingsUseCase(final PlatformSettingsRepository repository) {
        this.repository = repository;
    }

    public PlatformSettings execute(final String chatSystemPrompt, final Double chatTemperature) {
        if (chatTemperature != null && (chatTemperature < MIN_TEMPERATURE || chatTemperature > MAX_TEMPERATURE)) {
            throw new IllegalArgumentException("chat_temperature_out_of_range");
        }
        final PlatformSettings s = repository.get();
        s.setChatSystemPrompt(chatSystemPrompt == null || chatSystemPrompt.isBlank() ? null : chatSystemPrompt);
        s.setChatTemperature(chatTemperature);
        repository.save(s);
        return s;
    }
}
