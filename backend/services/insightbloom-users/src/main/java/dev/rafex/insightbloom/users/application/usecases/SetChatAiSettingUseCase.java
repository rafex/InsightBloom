package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.PlatformSettings;
import dev.rafex.insightbloom.users.domain.ports.PlatformSettingsRepository;

public class SetChatAiSettingUseCase {
    private final PlatformSettingsRepository repository;

    public SetChatAiSettingUseCase(final PlatformSettingsRepository repository) {
        this.repository = repository;
    }

    public PlatformSettings execute(final boolean chatAiEnabled) {
        final PlatformSettings s = new PlatformSettings();
        s.setChatAiEnabled(chatAiEnabled);
        repository.save(s);
        return s;
    }
}
