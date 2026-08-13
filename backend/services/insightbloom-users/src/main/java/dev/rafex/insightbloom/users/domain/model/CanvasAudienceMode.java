package dev.rafex.insightbloom.users.domain.model;

/** Modalidad de acceso/edición al lienzo de un evento. */
public enum CanvasAudienceMode {
    COLLABORATIVE,
    INDEPENDENT,
    MODERATOR_ONLY;

    /** Parseo tolerante: null si el texto no coincide con ningún valor conocido. */
    public static CanvasAudienceMode parse(final String raw) {
        if (raw == null) return null;
        for (final CanvasAudienceMode mode : values()) {
            if (mode.name().equals(raw)) return mode;
        }
        return null;
    }
}
