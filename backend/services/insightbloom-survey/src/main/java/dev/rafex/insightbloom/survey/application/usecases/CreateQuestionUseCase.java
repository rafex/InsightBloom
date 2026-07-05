package dev.rafex.insightbloom.survey.application.usecases;

import dev.rafex.insightbloom.survey.domain.model.QuestionType;
import dev.rafex.insightbloom.survey.domain.model.SurveyQuestion;
import dev.rafex.insightbloom.survey.domain.ports.SurveyQuestionRepository;

import java.util.List;
import java.util.UUID;

public class CreateQuestionUseCase {
    private final SurveyQuestionRepository repo;

    public CreateQuestionUseCase(final SurveyQuestionRepository repo) { this.repo = repo; }

    public record Request(String conferenceUuid, String text, String type, List<String> options,
                           String referenceAnswer, String ratingStyle, int orderIndex, Boolean required) {}

    public SurveyQuestion execute(final Request req) {
        if (req.text() == null || req.text().isBlank()) {
            throw new IllegalArgumentException("text_required");
        }
        final QuestionType type = QuestionType.valueOf((req.type() == null ? "TEXT" : req.type()).toUpperCase());
        final boolean required = req.required() == null || req.required();
        final var question = new SurveyQuestion(
                UUID.randomUUID().toString(), req.conferenceUuid(), req.text(), type, req.options(),
                req.referenceAnswer(), req.ratingStyle(), req.orderIndex(), required);
        repo.save(question);
        return question;
    }
}
