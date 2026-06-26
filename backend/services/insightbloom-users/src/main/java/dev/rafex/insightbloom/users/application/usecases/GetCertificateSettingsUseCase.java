package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.CertificateSettings;
import dev.rafex.insightbloom.users.domain.ports.CertificateSettingsRepository;

public class GetCertificateSettingsUseCase {
    private final CertificateSettingsRepository repository;

    public GetCertificateSettingsUseCase(final CertificateSettingsRepository repository) {
        this.repository = repository;
    }

    public CertificateSettings execute() {
        return repository.get();
    }
}
