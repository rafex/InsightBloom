package dev.rafex.insightbloom.survey.application.usecases;

import dev.rafex.insightbloom.survey.domain.model.AiSurveyConfig;
import dev.rafex.insightbloom.survey.domain.ports.AiSurveyConfigRepository;

import java.time.Instant;

public class AiSurveyConfigUseCase {
    private static final int MAX_EXTRA_CONTEXT = 4000;
    private final AiSurveyConfigRepository repository;

    public AiSurveyConfigUseCase(final AiSurveyConfigRepository repository) {
        this.repository = repository;
    }

    public AiSurveyConfig get(final String conferenceUuid) {
        return repository.findByConference(conferenceUuid).orElseGet(() ->
                new AiSurveyConfig(conferenceUuid, null, Instant.now()));
    }

    public AiSurveyConfig save(final String conferenceUuid, final String extraContext) {
        if (conferenceUuid == null || conferenceUuid.isBlank()) throw new IllegalArgumentException("conference_required");
        if (extraContext != null && extraContext.length() > MAX_EXTRA_CONTEXT) {
            throw new IllegalArgumentException("extra_context_too_long");
        }
        return repository.save(new AiSurveyConfig(conferenceUuid, clean(extraContext), Instant.now()));
    }

    private static String clean(final String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }
}
