package dev.rafex.insightbloom.users.domain.ports;

import dev.rafex.insightbloom.users.domain.model.CertificateSettings;

public interface CertificateSettingsRepository {
    CertificateSettings get();
    void save(CertificateSettings settings);
}
