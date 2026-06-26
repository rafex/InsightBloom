package dev.rafex.insightbloom.survey.application.usecases;

import dev.rafex.insightbloom.survey.domain.ports.SurveyResponseRepository;

public class PurgeResponsesUseCase {
    private final SurveyResponseRepository responseRepo;

    public PurgeResponsesUseCase(final SurveyResponseRepository responseRepo) {
        this.responseRepo = responseRepo;
    }

    public void execute(final String questionUuid) {
        responseRepo.deleteByQuestion(questionUuid);
    }
}
