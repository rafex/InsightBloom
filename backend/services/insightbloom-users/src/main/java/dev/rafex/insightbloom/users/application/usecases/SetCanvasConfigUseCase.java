package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.Conference;
import dev.rafex.insightbloom.users.domain.ports.ConferenceRepository;

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

    public Optional<Conference> execute(final String conferenceUuid, final String requestingUserUuid,
                                        final String canvasTool, final String audienceMode) {
        if (canvasTool != null && !canvasTool.isBlank()
                && !DRAWIO.equals(canvasTool) && !EXCALIDRAW.equals(canvasTool) && !ETHERPAD.equals(canvasTool)) {
            throw new IllegalArgumentException("canvas_tool_invalid");
        }
        if (audienceMode != null && !audienceMode.isBlank()
                && !INDEPENDENT.equals(audienceMode) && !MODERATOR_ONLY.equals(audienceMode)) {
            throw new IllegalArgumentException("canvas_audience_mode_invalid");
        }
        return conferenceRepository.findByUuid(conferenceUuid)
                .filter(c -> c.getCreatedByUserUuid().equals(requestingUserUuid))
                .map(c -> {
                    c.setCanvasTool(blankToNull(canvasTool));
                    c.setCanvasAudienceMode(blankToNull(audienceMode));
                    conferenceRepository.save(c);
                    return c;
                });
    }

    private static String blankToNull(final String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
