package dev.rafex.insightbloom.users.domain.model;

/**
 * Catálogo fijo de "tecnologías" que un tipo de evento puede habilitar. Vive en código (no es
 * administrable) — solo la combinación de capacidades por tipo de evento es administrable por
 * el ADMIN vía {@link EventType}.
 */
public enum EventCapability {
    TICKETING_GENERAL,
    TICKETING_SEATED,
    SURVEY,
    PRESENTATION,
    WORD_CLOUD,
    CHAT_BOT,
    VIDEO_CONFERENCE,
    WHITEBOARD,
    DIAGRAMMING,
    COLLAB_NOTES,
    CODE_IDE
}
