package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.CanvasAudienceMode;
import dev.rafex.insightbloom.users.domain.model.CanvasConfig;
import dev.rafex.insightbloom.users.domain.model.CanvasTool;
import dev.rafex.insightbloom.users.domain.model.Conference;
import dev.rafex.insightbloom.users.domain.ports.ConferenceRepository;
import dev.rafex.insightbloom.users.domain.ports.EtherpadPort;

import java.util.Optional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Crea (de forma perezosa e idempotente) el pad de Etherpad de un evento en su primer acceso. */
public class GetOrCreateEventPadUseCase {
    private final ConferenceRepository conferenceRepository;
    private final EtherpadPort etherpadPort;
    private final String privatePadSecret;

    public GetOrCreateEventPadUseCase(final ConferenceRepository conferenceRepository,
                                       final EtherpadPort etherpadPort) {
        this(conferenceRepository, etherpadPort, "");
    }

    public GetOrCreateEventPadUseCase(final ConferenceRepository conferenceRepository,
                                       final EtherpadPort etherpadPort, final String privatePadSecret) {
        this.conferenceRepository = conferenceRepository;
        this.etherpadPort = etherpadPort;
        this.privatePadSecret = privatePadSecret == null ? "" : privatePadSecret;
    }

    public record PadInfo(String padId, boolean readOnly) {}

    /** Compatibilidad: siempre devuelve el pad real editable (usado por ExportEventNotesUseCase,
     *  que necesita el padId real para leer contenido, nunca el ID de solo-lectura). */
    public Optional<PadInfo> execute(final String conferenceUuid, final String userUuid) {
        return execute(conferenceUuid, userUuid, true);
    }

    public Optional<PadInfo> execute(final String conferenceUuid) {
        return execute(conferenceUuid, null, true);
    }

    public Optional<PadInfo> execute(final String conferenceUuid, final String userUuid, final boolean isModerator) {
        return conferenceRepository.findByUuid(conferenceUuid)
                .map(conference -> ensurePad(conference, userUuid, isModerator));
    }

    public static boolean isIndividual(final Conference conference) {
        return conference.getCanvasConfigs().stream()
                .filter(config -> CanvasTool.ETHERPAD.equals(config.tool()))
                .map(CanvasConfig::audienceMode)
                .findFirst()
                .map(CanvasAudienceMode.INDEPENDENT::equals)
                .orElse(CanvasTool.ETHERPAD.equals(conference.getCanvasTool())
                        && CanvasAudienceMode.INDEPENDENT.equals(conference.getCanvasAudienceMode()));
    }

    private PadInfo ensurePad(final Conference conference, final String userUuid, final boolean isModerator) {
        final String padId = isIndividual(conference) && userUuid != null
                ? privatePadId(conference.getUuid(), userUuid) : conference.getUuid();
        etherpadPort.ensurePadExists(padId);
        if (!isModerator && isModeratorOnly(conference)) {
            return new PadInfo(etherpadPort.getReadOnlyId(padId), true);
        }
        return new PadInfo(padId, false);
    }

    private static boolean isModeratorOnly(final Conference conference) {
        return conference.getCanvasConfigs().stream()
                .filter(config -> CanvasTool.ETHERPAD.equals(config.tool()))
                .map(CanvasConfig::audienceMode)
                .findFirst()
                .map(CanvasAudienceMode.MODERATOR_ONLY::equals)
                .orElse(CanvasTool.ETHERPAD.equals(conference.getCanvasTool())
                        && CanvasAudienceMode.MODERATOR_ONLY.equals(conference.getCanvasAudienceMode()));
    }

    private String privatePadId(final String conferenceUuid, final String userUuid) {
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            final byte[] bytes = digest.digest((privatePadSecret + ":" + conferenceUuid + ":" + userUuid)
                    .getBytes(StandardCharsets.UTF_8));
            return conferenceUuid + "--private--" + HexFormat.of().formatHex(bytes, 0, 16);
        } catch (final Exception e) {
            throw new IllegalStateException("private_pad_id_failed", e);
        }
    }

}
