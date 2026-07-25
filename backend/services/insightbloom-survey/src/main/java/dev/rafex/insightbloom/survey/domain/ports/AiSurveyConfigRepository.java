package dev.rafex.insightbloom.survey.domain.ports;

import dev.rafex.insightbloom.survey.domain.model.AiSurveyConfig;

import java.util.Optional;

public interface AiSurveyConfigRepository {
    Optional<AiSurveyConfig> findByConference(String conferenceUuid);

    AiSurveyConfig save(AiSurveyConfig config);

    void deleteByConference(String conferenceUuid);
}
