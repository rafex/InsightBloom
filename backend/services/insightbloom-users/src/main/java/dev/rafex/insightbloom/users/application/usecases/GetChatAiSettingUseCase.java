package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.PlatformSettings;
import dev.rafex.insightbloom.users.domain.ports.PlatformSettingsRepository;

public class GetChatAiSettingUseCase {
    private final PlatformSettingsRepository repository;

    public GetChatAiSettingUseCase(final PlatformSettingsRepository repository) {
        this.repository = repository;
    }

    public PlatformSettings execute() {
        return repository.get();
    }
}
