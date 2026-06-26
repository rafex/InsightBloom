package dev.rafex.insightbloom.survey.application.usecases;

import dev.rafex.ether.json.JsonCodec;
import dev.rafex.insightbloom.survey.domain.ports.LlmPort;
import dev.rafex.insightbloom.survey.domain.ports.PresentationsPort;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class ImproveQuestionUseCase {
    private static final Pattern FRONTMATTER = Pattern.compile("^---.*?---\\s*", Pattern.DOTALL);
    private static final Pattern HTML_COMMENT = Pattern.compile("<!--.*?-->", Pattern.DOTALL);

    private static final String SYSTEM_PROMPT = """
            Eres un asistente que ayuda a un instructor a redactar mejor una pregunta de un
            cuestionario de evaluacion para una charla o taller. Te dan un borrador de pregunta
            (texto, tipo y, si aplica, opciones/respuesta de referencia) y, opcionalmente, el
            contenido visible de las diapositivas como contexto. Propone 3 alternativas mejoradas:
            mejor redaccion (mas clara y especifica), y el tipo de pregunta mas adecuado para lo
            que se quiere evaluar (puede ser el mismo tipo del borrador u otro mejor). Si el
            contenido de las diapositivas esta disponible, usalo solo como contexto para que la
            pregunta sea mas precisa, sin inventar datos que no aparezcan ahi.
            Responde UNICAMENTE con un arreglo JSON valido de exactamente 3 elementos (sin texto
            adicional, sin markdown), cada uno con esta forma exacta:
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

    public ImproveQuestionUseCase(final LlmPort llm, final PresentationsPort presentations,
                                   final JsonCodec jsonCodec) {
        this.llm = llm;
        this.presentations = presentations;
        this.jsonCodec = jsonCodec;
    }

    public record Request(String conferenceUuid, String text, String type, List<String> options,
                           String referenceAnswer) {}

    public record ImprovedQuestion(String text, String type, List<String> options, String referenceAnswer) {}

    public List<ImprovedQuestion> execute(final Request req) {
        if (!llm.isEnabled()) {
            throw new IllegalStateException("llm_not_configured");
        }
        if (req.text() == null || req.text().isBlank()) {
            throw new IllegalArgumentException("text_required");
        }

        final StringBuilder userPrompt = new StringBuilder("Borrador de pregunta:\n");
        userPrompt.append("Texto: ").append(req.text()).append('\n');
        userPrompt.append("Tipo: ").append(req.type() == null ? "TEXT" : req.type()).append('\n');
        if (req.options() != null && !req.options().isEmpty()) {
            userPrompt.append("Opciones: ").append(String.join(", ", req.options())).append('\n');
        }
        if (req.referenceAnswer() != null && !req.referenceAnswer().isBlank()) {
            userPrompt.append("Respuesta de referencia: ").append(req.referenceAnswer()).append('\n');
        }

        presentations.fetchMarkdown(req.conferenceUuid()).ifPresent(markdown ->
                userPrompt.append("\nContenido visible de las diapositivas (contexto):\n")
                        .append(stripNonVisibleContent(markdown)));

        final String raw = llm.complete(SYSTEM_PROMPT, userPrompt.toString());
        final String jsonArray = extractJsonArray(raw);

        @SuppressWarnings("unchecked")
        final List<Map<String, Object>> parsed = jsonCodec.readValue(jsonArray, List.class);
        return parsed.stream()
                .map(m -> new ImprovedQuestion(
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
