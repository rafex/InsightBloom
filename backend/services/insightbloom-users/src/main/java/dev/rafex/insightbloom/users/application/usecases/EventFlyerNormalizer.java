package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.services.ImageNormalizer;

/** Valida y normaliza flyers sin imponer el tamaño reducido de una foto de perfil. */
final class EventFlyerNormalizer {
    private static final ImageNormalizer.Options OPTIONS = new ImageNormalizer.Options(
            11_500_000, 8 * 1024 * 1024, 4096, 2048, false, "flyer");

    private EventFlyerNormalizer() {}

    static String normalize(final String dataUrl) {
        if (dataUrl == null || dataUrl.isBlank()) return null;
        return ImageNormalizer.normalize(dataUrl, OPTIONS);
    }
}
