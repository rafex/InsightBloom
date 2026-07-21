package dev.rafex.insightbloom.survey.application.usecases;

import dev.rafex.insightbloom.survey.domain.ports.SurveyQuestionRepository;
import dev.rafex.insightbloom.survey.domain.ports.SurveyResponseRepository;
import dev.rafex.insightbloom.survey.domain.ports.SurveyDefinitionRepository;
import dev.rafex.insightbloom.survey.domain.ports.SurveyJsSubmissionRepository;

public class DeleteConferenceDataUseCase {
    private final SurveyQuestionRepository questionRepo;
    private final SurveyResponseRepository responseRepo;
    private final SurveyDefinitionRepository definitionRepo;
    private final SurveyJsSubmissionRepository submissionRepo;

    public DeleteConferenceDataUseCase(final SurveyQuestionRepository questionRepo,
                                        final SurveyResponseRepository responseRepo,
                                        final SurveyDefinitionRepository definitionRepo,
                                        final SurveyJsSubmissionRepository submissionRepo) {
        this.questionRepo = questionRepo;
        this.responseRepo = responseRepo;
        this.definitionRepo = definitionRepo;
        this.submissionRepo = submissionRepo;
    }

    public void execute(final String conferenceUuid) {
        responseRepo.deleteByConference(conferenceUuid);
        questionRepo.deleteByConference(conferenceUuid);
        submissionRepo.deleteByConference(conferenceUuid);
        definitionRepo.deleteByConference(conferenceUuid);
    }
}
