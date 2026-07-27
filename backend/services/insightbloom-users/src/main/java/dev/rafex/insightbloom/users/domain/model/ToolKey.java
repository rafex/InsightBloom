package dev.rafex.insightbloom.users.domain.model;

/**
 * Herramientas del asistente que el moderador puede bloquear/liberar (2026-07-27). No incluye
 * Encuesta (tiene su propio candado en insightbloom-survey, ya existente) ni Mi boleto / Flyer
 * (siempre visibles, nunca se bloquean).
 */
public enum ToolKey {
    DOUBTS,
    TOPICS,
    PRESENTATION,
    CHAT,
    VIDEO,
    DIAGRAMS,
    WHITEBOARD,
    NOTES,
    IDE
}
