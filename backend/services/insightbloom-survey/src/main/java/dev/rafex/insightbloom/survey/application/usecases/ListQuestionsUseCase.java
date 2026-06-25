package dev.rafex.insightbloom.survey.application.usecases;

import dev.rafex.insightbloom.survey.domain.model.SurveyQuestion;
import dev.rafex.insightbloom.survey.domain.ports.SurveyQuestionRepository;

import java.util.List;

public class ListQuestionsUseCase {
    private final SurveyQuestionRepository repo;

    public ListQuestionsUseCase(final SurveyQuestionRepository repo) { this.repo = repo; }

    public List<SurveyQuestion> execute(final String conferenceUuid, final boolean onlyActive) {
        return repo.findByConference(conferenceUuid, onlyActive);
    }
}
