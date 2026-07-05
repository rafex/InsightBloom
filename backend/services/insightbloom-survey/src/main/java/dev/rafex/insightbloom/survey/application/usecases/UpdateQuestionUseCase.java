package dev.rafex.insightbloom.survey.application.usecases;

import dev.rafex.insightbloom.survey.domain.model.QuestionType;
import dev.rafex.insightbloom.survey.domain.model.SurveyQuestion;
import dev.rafex.insightbloom.survey.domain.ports.SurveyQuestionRepository;

import java.util.List;

public class UpdateQuestionUseCase {
    private final SurveyQuestionRepository repo;

    public UpdateQuestionUseCase(final SurveyQuestionRepository repo) { this.repo = repo; }

    public record Request(String questionUuid, String text, String type, List<String> options,
                           String referenceAnswer, String ratingStyle, Integer orderIndex, Boolean required) {}

    public SurveyQuestion execute(final Request req) {
        final SurveyQuestion question = repo.findByUuid(req.questionUuid())
                .orElseThrow(() -> new IllegalArgumentException("question_not_found"));
        if (req.text() == null || req.text().isBlank()) {
            throw new IllegalArgumentException("text_required");
        }
        final QuestionType type = QuestionType.valueOf((req.type() == null ? "TEXT" : req.type()).toUpperCase());
        final int orderIndex = req.orderIndex() == null ? question.getOrderIndex() : req.orderIndex();
        final boolean required = req.required() == null ? question.isRequired() : req.required();
        question.update(req.text(), type, req.options(), req.referenceAnswer(), req.ratingStyle(), orderIndex, required);
        repo.save(question);
        return question;
    }
}
