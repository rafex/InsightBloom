package dev.rafex.insightbloom.survey.domain.ports;

import dev.rafex.insightbloom.survey.domain.model.SurveyDefinition;

import java.util.Optional;

public interface SurveyDefinitionRepository {
    Optional<SurveyDefinition> findByConference(String conferenceUuid);
    void save(SurveyDefinition definition);
    void deleteByConference(String conferenceUuid);
}
