package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.ImagePolicy;
import dev.rafex.insightbloom.users.domain.ports.ImagePolicyRepository;

import java.time.Instant;

/** Lista blanca/negra de imágenes de contenedor POR EVENTO -- se suma a la global (ver
 *  ResolveImagePolicyUseCase). Mismo patrón que EgressPolicyUseCase. */
public class ImagePolicyUseCase {
    private static final int MAX_IMAGES_TEXT_LENGTH = 20_000;

    private final ImagePolicyRepository repository;

    public ImagePolicyUseCase(final ImagePolicyRepository repository) {
        this.repository = repository;
    }

    public ImagePolicy get(final String conferenceUuid) {
        return repository.findByConference(conferenceUuid).orElseGet(() ->
                new ImagePolicy(conferenceUuid, null, null, Instant.now()));
    }

    public ImagePolicy save(final String conferenceUuid, final String allowedImages, final String blockedImages) {
        if (conferenceUuid == null || conferenceUuid.isBlank()) throw new IllegalArgumentException("conference_required");
        if (allowedImages != null && allowedImages.length() > MAX_IMAGES_TEXT_LENGTH) {
            throw new IllegalArgumentException("allowed_images_too_long");
        }
        if (blockedImages != null && blockedImages.length() > MAX_IMAGES_TEXT_LENGTH) {
            throw new IllegalArgumentException("blocked_images_too_long");
        }
        return repository.save(new ImagePolicy(conferenceUuid, allowedImages, blockedImages, Instant.now()));
    }
}
