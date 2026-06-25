package dev.rafex.insightbloom.survey.domain.ports;

import dev.rafex.insightbloom.survey.domain.model.SurveyResponse;

import java.util.List;

public interface SurveyResponseRepository {
    void save(SurveyResponse response);
    List<SurveyResponse> findByConference(String conferenceUuid);
    List<SurveyResponse> findByQuestion(String questionUuid);
}
