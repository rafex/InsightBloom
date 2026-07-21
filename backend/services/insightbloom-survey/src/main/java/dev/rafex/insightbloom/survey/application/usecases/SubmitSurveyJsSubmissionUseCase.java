package dev.rafex.insightbloom.survey.application.usecases;

import dev.rafex.ether.json.JsonCodec;
import dev.rafex.insightbloom.survey.domain.model.SurveyDefinition;
import dev.rafex.insightbloom.survey.domain.model.SurveyEngine;
import dev.rafex.insightbloom.survey.domain.model.SurveyJsSubmission;
import dev.rafex.insightbloom.survey.domain.ports.SurveyDefinitionRepository;
import dev.rafex.insightbloom.survey.domain.ports.SurveyJsSubmissionRepository;

import java.util.Map;
import java.util.UUID;

public class SubmitSurveyJsSubmissionUseCase {
    private final SurveyDefinitionRepository definitionRepo;
    private final SurveyJsSubmissionRepository submissionRepo;
    private final JsonCodec jsonCodec;

    public SubmitSurveyJsSubmissionUseCase(final SurveyDefinitionRepository definitionRepo,
                                           final SurveyJsSubmissionRepository submissionRepo,
                                           final JsonCodec jsonCodec) {
        this.definitionRepo = definitionRepo;
        this.submissionRepo = submissionRepo;
        this.jsonCodec = jsonCodec;
    }

    public boolean hasResponded(final String conferenceUuid, final String userUuid) {
        return submissionRepo.existsByUserAndConference(conferenceUuid, userUuid);
    }

    public void execute(final String conferenceUuid, final String userUuid, final Map<String, Object> data) {
        if (data == null || data.isEmpty()) throw new IllegalArgumentException("data_required");
        final SurveyDefinition definition = definitionRepo.findByConference(conferenceUuid)
                .orElseThrow(() -> new IllegalStateException("survey_not_configured"));
        if (definition.getEngine() != SurveyEngine.SURVEYJS || !"PUBLISHED".equals(definition.getStatus())) {
            throw new IllegalStateException("survey_not_published");
        }
        if (submissionRepo.existsByUserAndConference(conferenceUuid, userUuid)) {
            throw new IllegalStateException("already_responded");
        }
        submissionRepo.save(new SurveyJsSubmission(UUID.randomUUID().toString(), conferenceUuid,
                definition.getUuid(), definition.getSchemaVersion(), userUuid, jsonCodec.toJson(data),
                java.time.Instant.now()));
    }
}
