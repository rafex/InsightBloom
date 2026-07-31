package dev.rafex.insightbloom.users.domain.model;

/**
 * Herramientas y acciones del asistente que el moderador puede bloquear/liberar (2026-07-27). No incluye
 * Encuesta (tiene su propio candado en insightbloom-survey, ya existente) ni Mi boleto / Flyer
 * (siempre visibles, nunca se bloquean). Las tres acciones de entrega del IDE son independientes
 * de la entrada al IDE: descargar el workspace, publicar una página o publicar un backend/API.
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
    IDE,
    IDE_DOWNLOAD,
    IDE_PUBLISH_PAGE,
    IDE_PUBLISH_API
}
