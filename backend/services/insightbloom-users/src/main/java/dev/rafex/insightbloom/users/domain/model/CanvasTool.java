package dev.rafex.insightbloom.users.domain.model;

import java.util.EnumSet;
import java.util.Set;

/**
 * Herramientas de lienzo colaborativo que un evento puede habilitar. Cada una declara, en un solo
 * lugar, qué capacidad del tipo de evento requiere y qué {@link CanvasAudienceMode} acepta --
 * antes esta regla vivía repetida (y a veces desincronizada) en varios use cases y el handler.
 */
public enum CanvasTool {
    DRAWIO(EventCapability.DIAGRAMMING, CanvasAudienceMode.INDEPENDENT,
            EnumSet.of(CanvasAudienceMode.INDEPENDENT, CanvasAudienceMode.MODERATOR_ONLY)),
    EXCALIDRAW(EventCapability.WHITEBOARD, CanvasAudienceMode.INDEPENDENT,
            EnumSet.of(CanvasAudienceMode.INDEPENDENT, CanvasAudienceMode.MODERATOR_ONLY)),
    // Etherpad no tiene modo de publicación exclusiva del moderador via API nativa; INDEPENDENT
    // usa un pad privado por asistente en vez de "solo lectura para todos menos uno".
    ETHERPAD(EventCapability.COLLAB_NOTES, CanvasAudienceMode.COLLABORATIVE,
            EnumSet.of(CanvasAudienceMode.COLLABORATIVE, CanvasAudienceMode.INDEPENDENT,
                    CanvasAudienceMode.MODERATOR_ONLY));

    private final EventCapability requiredCapability;
    private final CanvasAudienceMode defaultAudienceMode;
    private final Set<CanvasAudienceMode> supportedModes;

    CanvasTool(final EventCapability requiredCapability, final CanvasAudienceMode defaultAudienceMode,
               final Set<CanvasAudienceMode> supportedModes) {
        this.requiredCapability = requiredCapability;
        this.defaultAudienceMode = defaultAudienceMode;
        this.supportedModes = supportedModes;
    }

    public EventCapability requiredCapability() { return requiredCapability; }

    public CanvasAudienceMode defaultAudienceMode() { return defaultAudienceMode; }

    public boolean supports(final CanvasAudienceMode mode) {
        return mode != null && supportedModes.contains(mode);
    }

    /** Parseo tolerante: null si el texto no coincide con ningún valor conocido. */
    public static CanvasTool parse(final String raw) {
        if (raw == null) return null;
        for (final CanvasTool tool : values()) {
            if (tool.name().equals(raw)) return tool;
        }
        return null;
    }
}
