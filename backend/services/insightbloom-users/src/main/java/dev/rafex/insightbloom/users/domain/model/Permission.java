package dev.rafex.insightbloom.users.domain.model;

/**
 * Catálogo fijo de permisos que un {@link Role} puede combinar. Vive en código (no es
 * administrable) — solo la combinación de permisos por rol es administrable por un usuario con
 * `MANAGE_USERS` vía {@link Role}. Ver DEC-0021.
 */
public enum Permission {
    MANAGE_USERS,
    MANAGE_EVENT_TYPES,
    HOST_EVENT,
    MANAGE_EVENT_SETTINGS,
    ASSIGN_EVENT_ROLES,
    MANAGE_TICKETS,
    MODERATE_CONTENT,
    CHECK_IN,
    MANAGE_PRESENTATION,
    MANAGE_SURVEY,
    MANAGE_CERTIFICATE,
    VIDEO_MODERATE
}
