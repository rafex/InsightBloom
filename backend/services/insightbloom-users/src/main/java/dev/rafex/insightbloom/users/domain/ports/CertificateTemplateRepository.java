package dev.rafex.insightbloom.users.domain.ports;

import dev.rafex.insightbloom.users.domain.model.CertificateTemplate;

import java.util.Optional;

public interface CertificateTemplateRepository {
    Optional<CertificateTemplate> findByConferenceUuid(String conferenceUuid);
    void save(CertificateTemplate template);
}
