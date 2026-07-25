package dev.rafex.insightbloom.survey.application.usecases;

import dev.rafex.insightbloom.survey.domain.ports.SurveyQuestionRepository;
import dev.rafex.insightbloom.survey.domain.ports.SurveyResponseRepository;
import dev.rafex.insightbloom.survey.domain.ports.SurveyDefinitionRepository;
import dev.rafex.insightbloom.survey.domain.ports.SurveyJsSubmissionRepository;
import dev.rafex.insightbloom.survey.domain.ports.SurveyAccessRepository;
import dev.rafex.insightbloom.survey.domain.ports.AiMentorConfigRepository;
import dev.rafex.insightbloom.survey.domain.ports.AiSurveyConfigRepository;

public class DeleteConferenceDataUseCase {
    private final SurveyQuestionRepository questionRepo;
    private final SurveyResponseRepository responseRepo;
    private final SurveyDefinitionRepository definitionRepo;
    private final SurveyJsSubmissionRepository submissionRepo;
    private final SurveyAccessRepository accessRepo;
    private final AiMentorConfigRepository aiMentorConfigRepo;
    private final AiSurveyConfigRepository aiSurveyConfigRepo;

    public DeleteConferenceDataUseCase(final SurveyQuestionRepository questionRepo,
                                        final SurveyResponseRepository responseRepo,
                                        final SurveyDefinitionRepository definitionRepo,
                                        final SurveyJsSubmissionRepository submissionRepo,
                                        final SurveyAccessRepository accessRepo,
                                        final AiMentorConfigRepository aiMentorConfigRepo,
                                        final AiSurveyConfigRepository aiSurveyConfigRepo) {
        this.questionRepo = questionRepo;
        this.responseRepo = responseRepo;
        this.definitionRepo = definitionRepo;
        this.submissionRepo = submissionRepo;
        this.accessRepo = accessRepo;
        this.aiMentorConfigRepo = aiMentorConfigRepo;
        this.aiSurveyConfigRepo = aiSurveyConfigRepo;
    }

    public void execute(final String conferenceUuid) {
        responseRepo.deleteByConference(conferenceUuid);
        questionRepo.deleteByConference(conferenceUuid);
        submissionRepo.deleteByConference(conferenceUuid);
        definitionRepo.deleteByConference(conferenceUuid);
        accessRepo.deleteByConference(conferenceUuid);
        aiMentorConfigRepo.deleteByConference(conferenceUuid);
        aiSurveyConfigRepo.deleteByConference(conferenceUuid);
    }
}
