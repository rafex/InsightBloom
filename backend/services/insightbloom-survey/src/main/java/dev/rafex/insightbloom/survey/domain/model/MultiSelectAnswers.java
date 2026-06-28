package dev.rafex.insightbloom.survey.domain.model;

import dev.rafex.ether.json.JsonUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Codifica/decodifica respuestas de selección múltiple (MULTIPLE_CHOICE) como un JSON array de
 * strings, tanto para las opciones correctas (en {@code SurveyQuestion.referenceAnswer}) como
 * para las opciones elegidas por el participante (en {@code SurveyResponse.answerText}).
 *
 * <p>Tolera datos anteriores a este formato (un solo string plano, de cuando el tipo era de
 * selección única): si {@code raw} no es un JSON array válido, se trata como una selección de
 * una sola opción.
 */
public final class MultiSelectAnswers {

    private MultiSelectAnswers() {
    }

    public static List<String> parse(final String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        try {
            final var node = JsonUtils.codec().readTree(raw);
            if (node.isArray()) {
                final List<String> values = new ArrayList<>();
                node.forEach(el -> values.add(el.asText()));
                return values;
            }
        } catch (final Exception e) {
            // no era JSON: cae al fallback de abajo (string plano, formato anterior)
        }
        return List.of(raw);
    }
}
