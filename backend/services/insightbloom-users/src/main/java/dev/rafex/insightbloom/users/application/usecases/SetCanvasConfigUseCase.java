package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.Conference;
import dev.rafex.insightbloom.users.domain.model.CanvasConfig;
import dev.rafex.insightbloom.users.domain.ports.ConferenceRepository;

import java.util.List;
import java.util.Optional;

/** Persiste la herramienta y la modalidad de acceso al lienzo de un evento. */
public class SetCanvasConfigUseCase {
    public static final String DRAWIO = "DRAWIO";
    public static final String EXCALIDRAW = "EXCALIDRAW";
    public static final String ETHERPAD = "ETHERPAD";
    public static final String INDEPENDENT = "INDEPENDENT";
    public static final String MODERATOR_ONLY = "MODERATOR_ONLY";

    private final ConferenceRepository conferenceRepository;

    public SetCanvasConfigUseCase(final ConferenceRepository conferenceRepository) {
        this.conferenceRepository = conferenceRepository;
    }

    /** Compatibilidad con el contrato anterior de una sola herramienta. */
    public Optional<Conference> execute(final String conferenceUuid, final String requestingUserUuid,
                                        final String canvasTool, final String audienceMode) {
        final List<CanvasConfig> configs = canvasTool == null || canvasTool.isBlank()
                ? List.of()
                : List.of(new CanvasConfig(canvasTool, audienceMode == null || audienceMode.isBlank()
                        ? INDEPENDENT : audienceMode));
        return execute(conferenceUuid, requestingUserUuid, configs);
    }

    public Optional<Conference> execute(final String conferenceUuid, final String requestingUserUuid,
                                        final List<CanvasConfig> configs) {
        validate(configs);
        return conferenceRepository.findByUuid(conferenceUuid)
                .filter(c -> c.getCreatedByUserUuid().equals(requestingUserUuid))
                .map(c -> {
                    final List<CanvasConfig> normalized = configs == null ? List.of() : List.copyOf(configs);
                    c.setCanvasConfigs(normalized);
                    // Los campos antiguos representan una sola configuración y se mantienen
                    // para clientes previos. Con varias herramientas quedan en null.
                    c.setCanvasTool(normalized.size() == 1 ? normalized.get(0).tool() : null);
                    c.setCanvasAudienceMode(normalized.size() == 1 ? normalized.get(0).audienceMode() : null);
                    conferenceRepository.save(c);
                    conferenceRepository.replaceCanvasConfigs(c.getUuid(), normalized);
                    return c;
                });
    }

    private static void validate(final List<CanvasConfig> configs) {
        if (configs == null) return;
        final java.util.Set<String> tools = new java.util.HashSet<>();
        for (final CanvasConfig config : configs) {
            if (config == null || config.tool() == null || config.tool().isBlank()) {
                throw new IllegalArgumentException("canvas_tool_invalid");
            }
            if (!tools.add(config.tool())) {
                throw new IllegalArgumentException("canvas_tool_duplicate");
            }
            if (!DRAWIO.equals(config.tool()) && !EXCALIDRAW.equals(config.tool())
                    && !ETHERPAD.equals(config.tool())) {
                throw new IllegalArgumentException("canvas_tool_invalid");
            }
            if (!INDEPENDENT.equals(config.audienceMode()) && !MODERATOR_ONLY.equals(config.audienceMode())) {
                throw new IllegalArgumentException("canvas_audience_mode_invalid");
            }
        }
    }
}
