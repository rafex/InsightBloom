package dev.rafex.insightbloom.users.application.usecases;

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

    public record PadInfo(String padId) {}

    public Optional<PadInfo> execute(final String conferenceUuid, final String userUuid) {
        return conferenceRepository.findByUuid(conferenceUuid).map(conference -> ensurePad(conference, userUuid));
    }

    public Optional<PadInfo> execute(final String conferenceUuid) {
        return execute(conferenceUuid, null);
    }

    public static boolean isIndividual(final Conference conference) {
        return conference.getCanvasConfigs().stream()
                .filter(config -> ETHERPAD.equals(config.tool()))
                .map(dev.rafex.insightbloom.users.domain.model.CanvasConfig::audienceMode)
                .findFirst()
                .map(INDEPENDENT::equals)
                .orElse(ETHERPAD.equals(conference.getCanvasTool())
                        && INDEPENDENT.equals(conference.getCanvasAudienceMode()));
    }

    private PadInfo ensurePad(final Conference conference, final String userUuid) {
        final String padId = isIndividual(conference) && userUuid != null
                ? privatePadId(conference.getUuid(), userUuid) : conference.getUuid();
        etherpadPort.ensurePadExists(padId);
        return new PadInfo(padId);
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

    private static final String ETHERPAD = "ETHERPAD";
    private static final String INDEPENDENT = "INDEPENDENT";
}
