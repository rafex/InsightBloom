package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.CanvasAudienceMode;
import dev.rafex.insightbloom.users.domain.model.CanvasConfig;
import dev.rafex.insightbloom.users.domain.model.CanvasTool;
import dev.rafex.insightbloom.users.domain.model.Conference;
import dev.rafex.insightbloom.users.domain.ports.ConferenceRepository;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Persiste la herramienta y la modalidad de acceso al lienzo de un evento. */
public class SetCanvasConfigUseCase {
    private final ConferenceRepository conferenceRepository;

    public SetCanvasConfigUseCase(final ConferenceRepository conferenceRepository) {
        this.conferenceRepository = conferenceRepository;
    }

    /** Compatibilidad con el contrato anterior de una sola herramienta. */
    public Optional<Conference> execute(final String conferenceUuid, final String requestingUserUuid,
                                        final String canvasTool, final String audienceMode) {
        if (canvasTool == null || canvasTool.isBlank()) {
            return execute(conferenceUuid, requestingUserUuid, List.<CanvasConfig>of());
        }
        final CanvasTool tool = CanvasTool.parse(canvasTool);
        final CanvasAudienceMode mode = audienceMode == null || audienceMode.isBlank()
                ? (tool == null ? null : tool.defaultAudienceMode())
                : CanvasAudienceMode.parse(audienceMode);
        return execute(conferenceUuid, requestingUserUuid, List.of(new CanvasConfig(tool, mode)));
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
        final Set<CanvasTool> tools = new HashSet<>();
        for (final CanvasConfig config : configs) {
            if (config == null || config.tool() == null) {
                throw new IllegalArgumentException("canvas_tool_invalid");
            }
            if (!tools.add(config.tool())) {
                throw new IllegalArgumentException("canvas_tool_duplicate");
            }
            if (!config.tool().supports(config.audienceMode())) {
                throw new IllegalArgumentException("canvas_audience_mode_invalid");
            }
        }
    }
}
