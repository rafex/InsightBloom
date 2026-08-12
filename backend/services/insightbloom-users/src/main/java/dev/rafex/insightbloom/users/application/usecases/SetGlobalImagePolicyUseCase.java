package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.PlatformSettings;
import dev.rafex.insightbloom.users.domain.ports.PlatformSettingsRepository;

/** Lista blanca/negra GLOBAL de imágenes de contenedor, configurable desde
 *  /dashboard/admin/image-policy. Mismo patrón que SetGlobalEgressPolicyUseCase. */
public class SetGlobalImagePolicyUseCase {
    private static final int MAX_IMAGES_TEXT_LENGTH = 20_000;

    private final PlatformSettingsRepository repository;

    public SetGlobalImagePolicyUseCase(final PlatformSettingsRepository repository) {
        this.repository = repository;
    }

    public PlatformSettings execute(final String allowedImages, final String blockedImages) {
        if (allowedImages != null && allowedImages.length() > MAX_IMAGES_TEXT_LENGTH) {
            throw new IllegalArgumentException("allowed_images_too_long");
        }
        if (blockedImages != null && blockedImages.length() > MAX_IMAGES_TEXT_LENGTH) {
            throw new IllegalArgumentException("blocked_images_too_long");
        }
        final PlatformSettings s = repository.get();
        s.setImageAllowList(allowedImages);
        s.setImageBlockList(blockedImages);
        repository.save(s);
        return s;
    }
}
