package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.Conference;
import dev.rafex.insightbloom.users.domain.model.OnDemandCuePoint;
import dev.rafex.insightbloom.users.domain.ports.ConferenceRepository;

import java.util.List;
import java.util.Set;

/**
 * Configura el video on-demand (tier gratuito: YouTube o un nodo publico de PeerTube) de un
 * evento, junto con las sugerencias de herramienta por timestamp ("cue points"). Sin
 * storage/transcodificacion propia: solo se guarda la URL que pega el organizador, la conversion
 * a URL de embed vive en el frontend.
 */
public class SetOnDemandVideoUseCase {
    private static final Set<String> VALID_PROVIDERS = Set.of("YOUTUBE", "PEERTUBE");
    private static final int MAX_URL_LENGTH = 2000;
    private static final int MAX_LABEL_LENGTH = 200;
    private static final int MAX_CUE_POINTS = 50;

    private final ConferenceRepository conferenceRepository;

    public SetOnDemandVideoUseCase(final ConferenceRepository conferenceRepository) {
        this.conferenceRepository = conferenceRepository;
    }

    public record CuePointInput(int atSeconds, String label, String toolPath) {}

    public Conference execute(final String conferenceUuid, final String requestingUserUuid,
                               final String provider, final String url,
                               final List<CuePointInput> cuePoints) {
        final String normalizedProvider = blankToNull(provider);
        if (normalizedProvider != null && !VALID_PROVIDERS.contains(normalizedProvider)) {
            throw new IllegalArgumentException("provider_invalid");
        }
        final String normalizedUrl = blankToNull(url);
        if (normalizedProvider != null) {
            if (normalizedUrl == null || !normalizedUrl.startsWith("https://")) {
                throw new IllegalArgumentException("url_invalid");
            }
            if (normalizedUrl.length() > MAX_URL_LENGTH) {
                throw new IllegalArgumentException("url_too_long");
            }
        }
        final List<CuePointInput> normalizedCuePoints = cuePoints == null ? List.of() : cuePoints;
        if (normalizedCuePoints.size() > MAX_CUE_POINTS) {
            throw new IllegalArgumentException("too_many_cue_points");
        }
        for (final CuePointInput cuePoint : normalizedCuePoints) {
            if (cuePoint == null || cuePoint.atSeconds() < 0) {
                throw new IllegalArgumentException("cue_point_at_seconds_invalid");
            }
            if (blankToNull(cuePoint.label()) == null || cuePoint.label().length() > MAX_LABEL_LENGTH) {
                throw new IllegalArgumentException("cue_point_label_invalid");
            }
            if (blankToNull(cuePoint.toolPath()) == null) {
                throw new IllegalArgumentException("cue_point_tool_path_invalid");
            }
        }

        final Conference conf = conferenceRepository.findByUuid(conferenceUuid)
                .filter(c -> c.getCreatedByUserUuid().equals(requestingUserUuid))
                .orElseThrow(() -> new IllegalArgumentException("conference_not_found"));

        conf.setOnDemandVideoProvider(normalizedProvider);
        conf.setOnDemandVideoUrl(normalizedProvider == null ? null : normalizedUrl);
        conferenceRepository.save(conf);

        final List<OnDemandCuePoint> toSave = normalizedCuePoints.stream()
                .map(c -> new OnDemandCuePoint(c.atSeconds(), c.label().trim(), c.toolPath().trim()))
                .toList();
        conferenceRepository.replaceOnDemandCuePoints(conferenceUuid, toSave);
        conf.setOnDemandCuePoints(toSave);

        return conf;
    }

    private static String blankToNull(final String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
