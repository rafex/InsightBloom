package dev.rafex.insightbloom.survey.application.usecases;

import dev.rafex.insightbloom.survey.domain.model.QuestionType;
import dev.rafex.insightbloom.survey.domain.model.SurveyQuestion;
import dev.rafex.insightbloom.survey.domain.model.SurveyResponse;
import dev.rafex.insightbloom.survey.domain.ports.SurveyQuestionRepository;
import dev.rafex.insightbloom.survey.domain.ports.SurveyResponseRepository;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class SubmitResponsesUseCase {
    private final SurveyQuestionRepository questionRepo;
    private final SurveyResponseRepository responseRepo;

    public SubmitResponsesUseCase(final SurveyQuestionRepository questionRepo,
                                   final SurveyResponseRepository responseRepo) {
        this.questionRepo = questionRepo;
        this.responseRepo = responseRepo;
    }

    public record Answer(String questionUuid, String text, Integer rating) {}

    public record Request(String conferenceUuid, List<Answer> answers, String userUuid) {}

    public boolean hasResponded(final String conferenceUuid, final String userUuid) {
        return responseRepo.existsByUserAndConference(conferenceUuid, userUuid);
    }

    public void execute(final Request req) {
        if (req.answers() == null || req.answers().isEmpty()) {
            throw new IllegalArgumentException("answers_required");
        }
        if (req.userUuid() != null && responseRepo.existsByUserAndConference(req.conferenceUuid(), req.userUuid())) {
            throw new IllegalStateException("already_responded");
        }
        final Map<String, Answer> answersByQuestion = req.answers().stream()
                .collect(Collectors.toMap(Answer::questionUuid, a -> a, (a, b) -> a));
        final boolean missingRequired = questionRepo.findByConference(req.conferenceUuid(), true).stream()
                .filter(SurveyQuestion::isRequired)
                .anyMatch(q -> {
                    final Answer a = answersByQuestion.get(q.getUuid());
                    return a == null || ((a.text() == null || a.text().isBlank()) && a.rating() == null);
                });
        if (missingRequired) {
            throw new IllegalArgumentException("required_question_missing");
        }
        final String respondentToken = UUID.randomUUID().toString();
        for (final Answer answer : req.answers()) {
            final SurveyQuestion question = questionRepo.findByUuid(answer.questionUuid())
                    .orElseThrow(() -> new IllegalArgumentException("question_not_found"));

            final var response = new SurveyResponse(
                    UUID.randomUUID().toString(), req.conferenceUuid(), answer.questionUuid(),
                    respondentToken, answer.text(), answer.rating());
            response.setUserUuid(req.userUuid());
            // Drag-drop se califica localmente (es determinista, no requiere IA).
            // OPEN_GRADED/CODE_GRADED se califican bajo demanda vía GradeResponsesUseCase.
            if (question.getType() == QuestionType.DRAG_DROP) {
                response.grade(dragDropScore(question, response), null);
            }
            responseRepo.save(response);
        }
    }

    private Double dragDropScore(final SurveyQuestion question, final SurveyResponse response) {
        final List<String> correctOrder = question.getOptions();
        if (correctOrder == null || correctOrder.isEmpty() || response.getAnswerText() == null) return null;
        final String[] submitted = response.getAnswerText().split(";;");
        int matches = 0;
        for (int i = 0; i < correctOrder.size() && i < submitted.length; i++) {
            if (correctOrder.get(i).equals(submitted[i])) matches++;
        }
        return 100.0 * matches / correctOrder.size();
    }
}
