package dev.rafex.insightbloom.survey.application.usecases;

import dev.rafex.insightbloom.survey.domain.model.QuestionType;
import dev.rafex.insightbloom.survey.domain.model.SurveyQuestion;
import dev.rafex.insightbloom.survey.domain.model.SurveyResponse;
import dev.rafex.insightbloom.survey.domain.ports.SurveyQuestionRepository;
import dev.rafex.insightbloom.survey.domain.ports.SurveyResponseRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GetResultsUseCase {
    private final SurveyQuestionRepository questionRepo;
    private final SurveyResponseRepository responseRepo;

    public GetResultsUseCase(final SurveyQuestionRepository questionRepo,
                              final SurveyResponseRepository responseRepo) {
        this.questionRepo = questionRepo;
        this.responseRepo = responseRepo;
    }

    public record GradedAnswer(String answerText, Double gradeScore, String gradeFeedback) {}

    public record QuestionResult(String questionUuid, String text, String type, int responseCount,
                                  Double averageRating, Map<String, Integer> counts, List<String> texts,
                                  Double averageGradeScore, List<GradedAnswer> gradedAnswers) {}

    public List<QuestionResult> execute(final String conferenceUuid) {
        final List<SurveyQuestion> questions = questionRepo.findByConference(conferenceUuid, false);
        final List<QuestionResult> results = new ArrayList<>();

        for (final SurveyQuestion q : questions) {
            final List<SurveyResponse> responses = responseRepo.findByQuestion(q.getUuid());

            Double avgRating = null;
            final Map<String, Integer> counts = new HashMap<>();
            final List<String> texts = new ArrayList<>();
            Double avgGrade = null;
            List<GradedAnswer> gradedAnswers = null;

            if (q.getType() == QuestionType.RATING) {
                final var ratings = responses.stream()
                        .map(SurveyResponse::getAnswerRating)
                        .filter(r -> r != null)
                        .toList();
                avgRating = ratings.isEmpty() ? null
                        : ratings.stream().mapToInt(Integer::intValue).average().orElse(0.0);
            } else if (q.getType() == QuestionType.MULTIPLE_CHOICE) {
                for (final SurveyResponse r : responses) {
                    if (r.getAnswerText() != null) {
                        counts.merge(r.getAnswerText(), 1, Integer::sum);
                    }
                }
            } else if (q.getType() == QuestionType.OPEN_GRADED || q.getType() == QuestionType.CODE_GRADED
                    || q.getType() == QuestionType.DRAG_DROP) {
                gradedAnswers = responses.stream()
                        .map(r -> new GradedAnswer(r.getAnswerText(), r.getGradeScore(), r.getGradeFeedback()))
                        .toList();
                final var scores = gradedAnswers.stream()
                        .map(GradedAnswer::gradeScore)
                        .filter(s -> s != null)
                        .toList();
                avgGrade = scores.isEmpty() ? null
                        : scores.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            } else {
                for (final SurveyResponse r : responses) {
                    if (r.getAnswerText() != null && !r.getAnswerText().isBlank()) {
                        texts.add(r.getAnswerText());
                    }
                }
            }

            results.add(new QuestionResult(
                    q.getUuid(), q.getText(), q.getType().name(), responses.size(), avgRating, counts, texts,
                    avgGrade, gradedAnswers));
        }
        return results;
    }
}
