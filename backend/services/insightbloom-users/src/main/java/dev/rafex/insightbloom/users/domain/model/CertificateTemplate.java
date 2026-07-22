package dev.rafex.insightbloom.users.domain.model;

import java.time.Instant;

/** Plantilla de certificado asociada a una conferencia. El documento es un JSON controlado. */
public final class CertificateTemplate {
    private final String conferenceUuid;
    private String templateKey;
    private String templateName;
    private String engine;
    private String documentJson;
    private int version;
    private String updatedByUserUuid;
    private Instant updatedAt;

    public CertificateTemplate(final String conferenceUuid, final String templateKey,
                               final String templateName, final String engine,
                               final String documentJson, final int version,
                               final String updatedByUserUuid, final Instant updatedAt) {
        this.conferenceUuid = conferenceUuid;
        this.templateKey = templateKey;
        this.templateName = templateName;
        this.engine = engine;
        this.documentJson = documentJson;
        this.version = version;
        this.updatedByUserUuid = updatedByUserUuid;
        this.updatedAt = updatedAt;
    }

    public String getConferenceUuid() { return conferenceUuid; }
    public String getTemplateKey() { return templateKey; }
    public String getTemplateName() { return templateName; }
    public String getEngine() { return engine; }
    public String getDocumentJson() { return documentJson; }
    public int getVersion() { return version; }
    public String getUpdatedByUserUuid() { return updatedByUserUuid; }
    public Instant getUpdatedAt() { return updatedAt; }
}
