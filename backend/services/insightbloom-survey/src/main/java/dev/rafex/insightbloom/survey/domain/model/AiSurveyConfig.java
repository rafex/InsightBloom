package dev.rafex.insightbloom.survey.domain.model;

import java.time.Instant;

/**
 * Contexto adicional por evento para sugerir preguntas de encuesta. El contenido de la
 * presentación siempre se usa (comportamiento global, no configurable); esto es solo texto
 * extra que el organizador quiere que la IA considere y que puede no estar explícito en las
 * diapositivas (objetivos del examen, temas a enfatizar, terminología esperada, etc).
 */
public record AiSurveyConfig(
        String conferenceUuid,
        String extraContext,
        Instant updatedAt) {
}
