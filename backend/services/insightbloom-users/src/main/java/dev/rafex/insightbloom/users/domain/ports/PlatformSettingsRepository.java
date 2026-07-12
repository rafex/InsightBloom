package dev.rafex.insightbloom.users.domain.ports;

import dev.rafex.insightbloom.users.domain.model.PlatformSettings;

public interface PlatformSettingsRepository {
    PlatformSettings get();
    void save(PlatformSettings settings);
}
