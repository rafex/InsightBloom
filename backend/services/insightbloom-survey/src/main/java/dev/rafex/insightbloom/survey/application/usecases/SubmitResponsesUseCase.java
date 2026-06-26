package dev.rafex.insightbloom.survey.application.usecases;

import dev.rafex.insightbloom.survey.domain.model.QuestionType;
import dev.rafex.insightbloom.survey.domain.model.SurveyQuestion;
import dev.rafex.insightbloom.survey.domain.model.SurveyResponse;
import dev.rafex.insightbloom.survey.domain.ports.LlmPort;
import dev.rafex.insightbloom.survey.domain.ports.SurveyQuestionRepository;
import dev.rafex.insightbloom.survey.domain.ports.SurveyResponseRepository;

import java.util.List;
import java.util.UUID;

public class SubmitResponsesUseCase {
    private static final String GRADING_SYSTEM_PROMPT = """
            Eres un asistente que califica respuestas de un cuestionario de evaluacion de una
            charla o taller tecnico. Compara la respuesta del asistente contra la respuesta de
            referencia / criterios proporcionados por el instructor. Responde UNICAMENTE con un
            JSON valido de la forma {"score": <numero 0-100>, "feedback": "<comentario breve>"},
            sin texto adicional.
            """;

    private final SurveyQuestionRepository questionRepo;
    private final SurveyResponseRepository responseRepo;
    private final LlmPort llm;

    public SubmitResponsesUseCase(final SurveyQuestionRepository questionRepo,
                                   final SurveyResponseRepository responseRepo,
                                   final LlmPort llm) {
        this.questionRepo = questionRepo;
        this.responseRepo = responseRepo;
        this.llm = llm;
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
        final String respondentToken = UUID.randomUUID().toString();
        for (final Answer answer : req.answers()) {
            final SurveyQuestion question = questionRepo.findByUuid(answer.questionUuid())
                    .orElseThrow(() -> new IllegalArgumentException("question_not_found"));

            final var response = new SurveyResponse(
                    UUID.randomUUID().toString(), req.conferenceUuid(), answer.questionUuid(),
                    respondentToken, answer.text(), answer.rating());
            response.setUserUuid(req.userUuid());
            grade(question, response);
            responseRepo.save(response);
        }
    }

    private void grade(final SurveyQuestion question, final SurveyResponse response) {
        if (response.getAnswerText() == null || response.getAnswerText().isBlank()) return;

        if (question.getType() == QuestionType.DRAG_DROP) {
            response.grade(dragDropScore(question, response), null);
            return;
        }
        final boolean gradeable = question.getType() == QuestionType.OPEN_GRADED
                || question.getType() == QuestionType.CODE_GRADED;
        if (!gradeable || question.getReferenceAnswer() == null || !llm.isEnabled()) return;

        try {
            final String userPrompt = "Pregunta: " + question.getText()
                    + "\n\nRespuesta de referencia / criterios: " + question.getReferenceAnswer()
                    + "\n\nRespuesta del asistente: " + response.getAnswerText();
            final String raw = llm.complete(GRADING_SYSTEM_PROMPT, userPrompt);
            final int scoreStart = raw.indexOf('{');
            final int scoreEnd = raw.lastIndexOf('}');
            if (scoreStart < 0 || scoreEnd < scoreStart) return;
            final String json = raw.substring(scoreStart, scoreEnd + 1);
            final var node = dev.rafex.ether.json.JsonUtils.codec().readTree(json);
            final double score = node.path("score").asDouble();
            final String feedback = node.path("feedback").asText(null);
            response.grade(score, feedback);
        } catch (final Exception e) {
            // best-effort: la respuesta queda guardada sin calificar si el LLM falla
        }
    }

    private Double dragDropScore(final SurveyQuestion question, final SurveyResponse response) {
        final List<String> correctOrder = question.getOptions();
        if (correctOrder == null || correctOrder.isEmpty()) return null;
        final String[] submitted = response.getAnswerText().split(";;");
        int matches = 0;
        for (int i = 0; i < correctOrder.size() && i < submitted.length; i++) {
            if (correctOrder.get(i).equals(submitted[i])) matches++;
        }
        return 100.0 * matches / correctOrder.size();
    }
}
