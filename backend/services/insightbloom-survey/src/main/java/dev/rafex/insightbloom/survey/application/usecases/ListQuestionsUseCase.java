package dev.rafex.insightbloom.survey.application.usecases;

import dev.rafex.insightbloom.survey.domain.model.QuestionType;
import dev.rafex.insightbloom.survey.domain.model.SurveyQuestion;
import dev.rafex.insightbloom.survey.domain.ports.SurveyQuestionRepository;

import java.util.List;

public class ListQuestionsUseCase {
    private final SurveyQuestionRepository repo;

    public ListQuestionsUseCase(final SurveyQuestionRepository repo) { this.repo = repo; }

    public record PublicQuestion(String uuid, String conferenceUuid, String text, String type,
                                  List<String> options, int orderIndex, boolean active) {}

    public List<SurveyQuestion> execute(final String conferenceUuid, final boolean onlyActive) {
        return repo.findByConference(conferenceUuid, onlyActive);
    }

    public List<PublicQuestion> executePublic(final String conferenceUuid) {
        return repo.findByConference(conferenceUuid, true).stream()
                .map(q -> {
                    final List<String> options = q.getType() == QuestionType.DRAG_DROP
                            ? shuffled(q.getOptions())
                            : q.getOptions();
                    return new PublicQuestion(q.getUuid(), q.getConferenceUuid(), q.getText(),
                            q.getType().name(), options, q.getOrderIndex(), q.isActive());
                })
                .toList();
    }

    private List<String> shuffled(final List<String> options) {
        if (options == null) return null;
        final var copy = new java.util.ArrayList<>(options);
        java.util.Collections.shuffle(copy);
        return copy;
    }
}
