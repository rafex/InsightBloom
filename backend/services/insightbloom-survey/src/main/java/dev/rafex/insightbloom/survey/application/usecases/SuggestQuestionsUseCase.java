package dev.rafex.insightbloom.survey.application.usecases;

import dev.rafex.ether.json.JsonCodec;
import dev.rafex.insightbloom.survey.domain.ports.LlmPort;
import dev.rafex.insightbloom.survey.domain.ports.PresentationsPort;

import java.util.List;
import java.util.Map;

public class SuggestQuestionsUseCase {
    private static final String SYSTEM_PROMPT = """
            Eres un asistente que ayuda a un instructor a crear un cuestionario de evaluacion
            para verificar si su audiencia entendio una charla o taller. A partir del contenido
            en markdown de la presentacion, propone preguntas variadas y relevantes.
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
    private final JsonCodec jsonCodec;

    public SuggestQuestionsUseCase(final LlmPort llm, final PresentationsPort presentations,
                                    final JsonCodec jsonCodec) {
        this.llm = llm;
        this.presentations = presentations;
        this.jsonCodec = jsonCodec;
    }

    public record SuggestedQuestion(String text, String type, List<String> options, String referenceAnswer) {}

    public List<SuggestedQuestion> execute(final String conferenceId, final int count) {
        if (!llm.isEnabled()) {
            throw new IllegalStateException("llm_not_configured");
        }
        final String markdown = presentations.fetchMarkdown(conferenceId)
                .orElseThrow(() -> new IllegalArgumentException("presentation_not_found"));

        final String userPrompt = "Genera " + count + " preguntas a partir de esta presentacion:\n\n" + markdown;
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
