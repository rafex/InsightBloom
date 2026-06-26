package dev.rafex.insightbloom.survey.application.usecases;

import dev.rafex.insightbloom.survey.domain.ports.SurveyQuestionRepository;
import dev.rafex.insightbloom.survey.domain.ports.SurveyResponseRepository;

public class DeleteConferenceDataUseCase {
    private final SurveyQuestionRepository questionRepo;
    private final SurveyResponseRepository responseRepo;

    public DeleteConferenceDataUseCase(final SurveyQuestionRepository questionRepo,
                                        final SurveyResponseRepository responseRepo) {
        this.questionRepo = questionRepo;
        this.responseRepo = responseRepo;
    }

    public void execute(final String conferenceUuid) {
        responseRepo.deleteByConference(conferenceUuid);
        questionRepo.deleteByConference(conferenceUuid);
    }
}
