package dev.rafex.insightbloom.survey.domain.ports;

import dev.rafex.insightbloom.survey.domain.model.SurveyQuestion;

import java.util.List;
import java.util.Optional;

public interface SurveyQuestionRepository {
    void save(SurveyQuestion question);
    Optional<SurveyQuestion> findByUuid(String uuid);
    List<SurveyQuestion> findByConference(String conferenceUuid, boolean onlyActive);
    void deleteByConference(String conferenceUuid);
}
