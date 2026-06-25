package dev.rafex.insightbloom.survey.application.usecases;

import dev.rafex.insightbloom.survey.domain.ports.SurveyQuestionRepository;

public class DeactivateQuestionUseCase {
    private final SurveyQuestionRepository repo;

    public DeactivateQuestionUseCase(final SurveyQuestionRepository repo) { this.repo = repo; }

    public void execute(final String questionUuid) {
        final var question = repo.findByUuid(questionUuid)
                .orElseThrow(() -> new IllegalArgumentException("question_not_found"));
        question.deactivate();
        repo.save(question);
    }
}
