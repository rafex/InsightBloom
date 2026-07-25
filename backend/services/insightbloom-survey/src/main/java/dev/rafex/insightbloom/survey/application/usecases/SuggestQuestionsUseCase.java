package dev.rafex.insightbloom.survey.application.usecases;

import dev.rafex.ether.json.JsonCodec;
import dev.rafex.insightbloom.survey.domain.ports.AiSurveyConfigRepository;
import dev.rafex.insightbloom.survey.domain.ports.LlmPort;
import dev.rafex.insightbloom.survey.domain.ports.PresentationsPort;
import dev.rafex.insightbloom.survey.domain.ports.SurveyQuestionRepository;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class SuggestQuestionsUseCase {
    private static final Pattern FRONTMATTER = Pattern.compile("^---.*?---\\s*", Pattern.DOTALL);
    private static final Pattern HTML_COMMENT = Pattern.compile("<!--.*?-->", Pattern.DOTALL);

    private static final String SYSTEM_PROMPT = """
            Eres un asistente que ayuda a un instructor a crear un cuestionario de evaluacion
            para verificar si su audiencia entendio una charla o taller. Recibiras el contenido
            visible de las diapositivas (lo que el publico vio en pantalla, sin notas del orador
            ni metadata) y, opcionalmente, contexto adicional que el organizador escribio a mano
            (objetivos del examen, temas a enfatizar, terminologia esperada). Basa las preguntas
            en el contenido visible y, cuando exista, en ese contexto adicional del organizador
            -- nunca inventes datos que no aparezcan en ninguno de los dos.
            Propone preguntas variadas y relevantes sobre lo que se mostro.
            Recibiras tambien la lista de preguntas que YA EXISTEN en el cuestionario. No repitas
            ninguna de ellas ni generes preguntas semanticamente equivalentes (mismo hecho o
            concepto preguntado de otra forma): cubre aspectos del contenido que esas preguntas
            todavia no cubren.
            Responde UNICAMENTE con un arreglo JSON valido (sin texto adicional, sin markdown),
            donde cada elemento tiene esta forma exacta:
            {"text": "...", "type": "RATING|TEXT|MULTIPLE_CHOICE|OPEN_GRADED|CODE_GRADED|DRAG_DROP",
             "options": ["..."] o null, "referenceAnswer": "..." o null}
            Reglas por tipo:
            - RATING: options=null, referenceAnswer=null.
            - MULTIPLE_CHOICE: options=lista de alternativas, referenceAnswer=null.
            - TEXT: options=null, referenceAnswer=null.
            - OPEN_GRADED: options=null, referenceAnswer=respuesta de referencia esperada.
            - CODE_GRADED: options=null, referenceAnswer=criterios o solucion esperada.
            - DRAG_DROP: options=lista de items en el ORDEN CORRECTO, referenceAnswer=null.
            """;

    private final LlmPort llm;
    private final PresentationsPort presentations;
    private final SurveyQuestionRepository questionRepo;
    private final AiSurveyConfigRepository surveyConfigRepo;
    private final JsonCodec jsonCodec;

    public SuggestQuestionsUseCase(final LlmPort llm, final PresentationsPort presentations,
                                    final SurveyQuestionRepository questionRepo,
                                    final AiSurveyConfigRepository surveyConfigRepo, final JsonCodec jsonCodec) {
        this.llm = llm;
        this.presentations = presentations;
        this.questionRepo = questionRepo;
        this.surveyConfigRepo = surveyConfigRepo;
        this.jsonCodec = jsonCodec;
    }

    public record SuggestedQuestion(String text, String type, List<String> options, String referenceAnswer) {}

    public List<SuggestedQuestion> execute(final String conferenceId, final int count) {
        if (!llm.isEnabled()) {
            throw new IllegalStateException("llm_not_configured");
        }
        final String markdown = presentations.fetchMarkdown(conferenceId)
                .orElseThrow(() -> new IllegalArgumentException("presentation_not_found"));
        final String visibleContent = stripNonVisibleContent(markdown);
        final String existingQuestions = questionRepo.findByConference(conferenceId, false).stream()
                .map(q -> "- " + q.getText())
                .collect(Collectors.joining("\n"));
        final String extraContext = surveyConfigRepo.findByConference(conferenceId)
                .map(config -> config.extraContext()).filter(value -> value != null && !value.isBlank())
                .orElse(null);

        final String userPrompt = "Genera " + count
                + " preguntas a partir del contenido visible de estas diapositivas:\n\n" + visibleContent
                + (extraContext == null ? "" : "\n\nCONTEXTO ADICIONAL DEL ORGANIZADOR (puede no estar explicito "
                        + "en las diapositivas, pero es informacion valida a considerar):\n" + extraContext)
                + "\n\nPreguntas que YA EXISTEN en el cuestionario (no las repitas ni generes equivalentes):\n"
                + (existingQuestions.isBlank() ? "(ninguna)" : existingQuestions);
        final String raw = llm.complete(SYSTEM_PROMPT, userPrompt);
        final String jsonArray = extractJsonArray(raw);

        @SuppressWarnings("unchecked")
        final List<Map<String, Object>> parsed = jsonCodec.readValue(jsonArray, List.class);
        return parsed.stream()
                .map(m -> new SuggestedQuestion(
                        (String) m.get("text"),
                        (String) m.getOrDefault("type", "TEXT"),
                        castOptions(m.get("options")),
                        (String) m.get("referenceAnswer")))
                .toList();
    }

    private String stripNonVisibleContent(final String markdown) {
        String result = FRONTMATTER.matcher(markdown).replaceFirst("");
        result = HTML_COMMENT.matcher(result).replaceAll("");
        return result.trim();
    }

    @SuppressWarnings("unchecked")
    private List<String> castOptions(final Object value) {
        return value instanceof List<?> list ? (List<String>) list : null;
    }

    private String extractJsonArray(final String raw) {
        final int start = raw.indexOf('[');
        final int end = raw.lastIndexOf(']');
        if (start < 0 || end < 0 || end < start) {
            throw new RuntimeException("llm_invalid_response: " + raw);
        }
        return raw.substring(start, end + 1);
    }
}
