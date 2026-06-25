package dev.rafex.insightbloom.survey.application.usecases;

import dev.rafex.insightbloom.survey.domain.model.SurveyResponse;
import dev.rafex.insightbloom.survey.domain.ports.SurveyQuestionRepository;
import dev.rafex.insightbloom.survey.domain.ports.SurveyResponseRepository;

import java.util.List;
import java.util.UUID;

public class SubmitResponsesUseCase {
    private final SurveyQuestionRepository questionRepo;
    private final SurveyResponseRepository responseRepo;

    public SubmitResponsesUseCase(final SurveyQuestionRepository questionRepo,
                                   final SurveyResponseRepository responseRepo) {
        this.questionRepo = questionRepo;
        this.responseRepo = responseRepo;
    }

    public record Answer(String questionUuid, String text, Integer rating) {}

    public record Request(String conferenceUuid, List<Answer> answers) {}

    public void execute(final Request req) {
        if (req.answers() == null || req.answers().isEmpty()) {
            throw new IllegalArgumentException("answers_required");
        }
        final String respondentToken = UUID.randomUUID().toString();
        for (final Answer answer : req.answers()) {
            questionRepo.findByUuid(answer.questionUuid())
                    .orElseThrow(() -> new IllegalArgumentException("question_not_found"));
            responseRepo.save(new SurveyResponse(
                    UUID.randomUUID().toString(), req.conferenceUuid(), answer.questionUuid(),
                    respondentToken, answer.text(), answer.rating()));
        }
    }
}
