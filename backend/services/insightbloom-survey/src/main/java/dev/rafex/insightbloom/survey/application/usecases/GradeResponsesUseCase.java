package dev.rafex.insightbloom.survey.application.usecases;

import dev.rafex.insightbloom.survey.domain.model.MultiSelectAnswers;
import dev.rafex.insightbloom.survey.domain.model.QuestionType;
import dev.rafex.insightbloom.survey.domain.model.SurveyQuestion;
import dev.rafex.insightbloom.survey.domain.model.SurveyResponse;
import dev.rafex.insightbloom.survey.domain.ports.LlmPort;
import dev.rafex.insightbloom.survey.domain.ports.SurveyQuestionRepository;
import dev.rafex.insightbloom.survey.domain.ports.SurveyResponseRepository;

import java.util.List;

/** Calificación por IA bajo demanda (no se dispara automáticamente al enviar la encuesta). */
public class GradeResponsesUseCase {
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

    public GradeResponsesUseCase(final SurveyQuestionRepository questionRepo,
                                  final SurveyResponseRepository responseRepo,
                                  final LlmPort llm) {
        this.questionRepo = questionRepo;
        this.responseRepo = responseRepo;
        this.llm = llm;
    }

    public record Request(String conferenceUuid, List<String> questionUuids, boolean regrade) {}
    public record Result(int graded, int skipped) {}

    public Result execute(final Request req) {
        final List<String> questionUuids = (req.questionUuids() == null || req.questionUuids().isEmpty())
                ? questionRepo.findByConference(req.conferenceUuid(), false).stream()
                        .map(SurveyQuestion::getUuid).toList()
                : req.questionUuids();

        int graded = 0;
        int skipped = 0;
        for (final String questionUuid : questionUuids) {
            final SurveyQuestion question = questionRepo.findByUuid(questionUuid).orElse(null);
            if (question == null) continue;

            final boolean llmGradeable = question.getType() == QuestionType.OPEN_GRADED
                    || question.getType() == QuestionType.CODE_GRADED;
            final boolean deterministicGradeable = question.getType() == QuestionType.MULTIPLE_CHOICE;
            if (!llmGradeable && !deterministicGradeable) continue;
            if (question.getReferenceAnswer() == null) continue;
            if (llmGradeable && !llm.isEnabled()) {
                throw new IllegalStateException("llm_not_configured");
            }

            for (final SurveyResponse response : responseRepo.findByQuestion(questionUuid)) {
                if (response.getAnswerText() == null || response.getAnswerText().isBlank()) continue;
                if (!req.regrade() && response.getGradeScore() != null) { skipped++; continue; }
                final boolean ok = deterministicGradeable
                        ? gradeMultipleChoice(question, response)
                        : gradeOne(question, response);
                if (ok) graded++; else skipped++;
            }
        }
        return new Result(graded, skipped);
    }

    /** Calificación determinística por "regla de tres": no importa el orden, no penaliza
     *  seleccionar opciones incorrectas además de las correctas. */
    private boolean gradeMultipleChoice(final SurveyQuestion question, final SurveyResponse response) {
        final List<String> correct = MultiSelectAnswers.parse(question.getReferenceAnswer());
        if (correct.isEmpty()) return false;
        final List<String> selected = MultiSelectAnswers.parse(response.getAnswerText());
        final long matched = selected.stream().filter(correct::contains).count();
        final double score = 100.0 * matched / correct.size();
        responseRepo.updateGrade(response.getUuid(), score, null);
        return true;
    }

    private boolean gradeOne(final SurveyQuestion question, final SurveyResponse response) {
        try {
            final String userPrompt = "Pregunta: " + question.getText()
                    + "\n\nRespuesta de referencia / criterios: " + question.getReferenceAnswer()
                    + "\n\nRespuesta del asistente: " + response.getAnswerText();
            final String raw = llm.complete(GRADING_SYSTEM_PROMPT, userPrompt);
            final int scoreStart = raw.indexOf('{');
            final int scoreEnd = raw.lastIndexOf('}');
            if (scoreStart < 0 || scoreEnd < scoreStart) return false;
            final String json = raw.substring(scoreStart, scoreEnd + 1);
            final var node = dev.rafex.ether.json.JsonUtils.codec().readTree(json);
            final double score = node.path("score").asDouble();
            final String feedback = node.path("feedback").asText(null);
            responseRepo.updateGrade(response.getUuid(), score, feedback);
            return true;
        } catch (final Exception e) {
            return false;
        }
    }
}
