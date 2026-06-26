package dev.rafex.insightbloom.survey.application.usecases;

import dev.rafex.insightbloom.survey.domain.model.QuestionType;
import dev.rafex.insightbloom.survey.domain.model.SurveyQuestion;
import dev.rafex.insightbloom.survey.domain.model.SurveyResponse;
import dev.rafex.insightbloom.survey.domain.ports.SurveyQuestionRepository;
import dev.rafex.insightbloom.survey.domain.ports.SurveyResponseRepository;

import java.time.Instant;
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

    public record IndividualAnswer(String answerText, Integer answerRating, Double gradeScore,
                                    String gradeFeedback, Instant submittedAt) {}

    public record QuestionResult(String questionUuid, String text, String type, String ratingStyle,
                                  int responseCount, Double averageRating, Map<String, Integer> counts,
                                  Double averageGradeScore, List<IndividualAnswer> individualAnswers) {}

    public List<QuestionResult> execute(final String conferenceUuid) {
        final List<SurveyQuestion> questions = questionRepo.findByConference(conferenceUuid, false);
        final List<QuestionResult> results = new ArrayList<>();

        for (final SurveyQuestion q : questions) {
            final List<SurveyResponse> responses = responseRepo.findByQuestion(q.getUuid());

            Double avgRating = null;
            final Map<String, Integer> counts = new HashMap<>();
            Double avgGrade = null;

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
                final var scores = responses.stream()
                        .map(SurveyResponse::getGradeScore)
                        .filter(s -> s != null)
                        .toList();
                avgGrade = scores.isEmpty() ? null
                        : scores.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            }

            final List<IndividualAnswer> individualAnswers = responses.stream()
                    .map(r -> new IndividualAnswer(r.getAnswerText(), r.getAnswerRating(), r.getGradeScore(),
                            r.getGradeFeedback(), r.getSubmittedAt()))
                    .sorted((a, b) -> b.submittedAt().compareTo(a.submittedAt()))
                    .toList();

            results.add(new QuestionResult(
                    q.getUuid(), q.getText(), q.getType().name(), q.getRatingStyle(), responses.size(), avgRating,
                    counts, avgGrade, individualAnswers));
        }
        return results;
    }
}
