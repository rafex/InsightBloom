package dev.rafex.insightbloom.survey.domain.ports;

import dev.rafex.insightbloom.survey.domain.model.SurveyJsSubmission;

import java.util.List;

public interface SurveyJsSubmissionRepository {
    void save(SurveyJsSubmission submission);
    boolean existsByUserAndConference(String conferenceUuid, String userUuid);
    List<SurveyJsSubmission> findByConference(String conferenceUuid);
    void deleteByConference(String conferenceUuid);
}
