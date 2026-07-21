package dev.rafex.insightbloom.survey.domain.model;

import java.time.Instant;

public class SurveyDefinition {
    private final String uuid;
    private final String conferenceUuid;
    private final SurveyEngine engine;
    private String schemaJson;
    private int schemaVersion;
    private String status;
    private final Instant createdAt;
    private Instant updatedAt;
    private Instant publishedAt;

    public SurveyDefinition(final String uuid, final String conferenceUuid, final SurveyEngine engine,
                            final String schemaJson, final int schemaVersion, final String status,
                            final Instant createdAt, final Instant updatedAt, final Instant publishedAt) {
        this.uuid = uuid;
        this.conferenceUuid = conferenceUuid;
        this.engine = engine;
        this.schemaJson = schemaJson;
        this.schemaVersion = schemaVersion;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.publishedAt = publishedAt;
    }

    public static SurveyDefinition draft(final String conferenceUuid, final SurveyEngine engine,
                                         final String schemaJson) {
        final Instant now = Instant.now();
        return new SurveyDefinition(java.util.UUID.randomUUID().toString(), conferenceUuid, engine,
                schemaJson, 1, "DRAFT", now, now, null);
    }

    public void updateDraft(final String schemaJson) {
        this.schemaJson = schemaJson;
        this.status = "DRAFT";
        this.updatedAt = Instant.now();
    }

    public void publish(final String schemaJson) {
        this.schemaJson = schemaJson;
        this.schemaVersion++;
        this.status = "PUBLISHED";
        this.publishedAt = Instant.now();
        this.updatedAt = this.publishedAt;
    }

    public String getUuid() { return uuid; }
    public String getConferenceUuid() { return conferenceUuid; }
    public SurveyEngine getEngine() { return engine; }
    public String getSchemaJson() { return schemaJson; }
    public int getSchemaVersion() { return schemaVersion; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getPublishedAt() { return publishedAt; }
}
