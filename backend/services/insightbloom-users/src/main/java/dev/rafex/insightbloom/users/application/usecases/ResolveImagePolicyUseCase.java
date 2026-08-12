package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.ImagePolicy;
import dev.rafex.insightbloom.users.domain.model.PlatformSettings;
import dev.rafex.insightbloom.users.domain.ports.ImagePolicyRepository;
import dev.rafex.insightbloom.users.domain.ports.PlatformSettingsRepository;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Resuelve, para un evento puntual, la política de imágenes de contenedor efectiva (allowed/
 * blocked ya combinados) -- llamado por ContainerfileValidator/PublishContainerUseCase antes de
 * intentar cualquier build.
 *
 * Misma herencia en capas que ResolveEgressPolicyUseCase: {@code allowed = global ∪ evento},
 * {@code blocked = global ∪ evento}. BLOCKED SIEMPRE GANA -- un evento no puede desbloquear una
 * imagen que la plataforma prohíbe, solo puede sumar sus propios bloqueos.
 */
public class ResolveImagePolicyUseCase {
    private final PlatformSettingsRepository platformSettingsRepository;
    private final ImagePolicyRepository imagePolicyRepository;

    public ResolveImagePolicyUseCase(final PlatformSettingsRepository platformSettingsRepository,
                                      final ImagePolicyRepository imagePolicyRepository) {
        this.platformSettingsRepository = platformSettingsRepository;
        this.imagePolicyRepository = imagePolicyRepository;
    }

    public record Resolution(Set<String> allowed, Set<String> blocked) {
        /** true si algún prefijo de blocked matchea la imagen (siempre gana), o si allowed no
         *  está vacío y ningún prefijo matchea (allowlist explícita = todo lo demás rechazado).
         *  allowed vacío == sin restricción de whitelist (solo rige blocked). */
        public boolean isAllowed(final String imageName) {
            final String lower = imageName.toLowerCase();
            if (blocked.stream().anyMatch(lower::startsWith)) return false;
            if (allowed.isEmpty()) return true;
            return allowed.stream().anyMatch(lower::startsWith);
        }
    }

    public Resolution execute(final String conferenceUuid) {
        final PlatformSettings global = platformSettingsRepository.get();
        final ImagePolicy event = conferenceUuid == null ? null
                : imagePolicyRepository.findByConference(conferenceUuid).orElse(null);

        final Set<String> allowed = union(global.getImageAllowList(), event == null ? null : event.allowedImages());
        final Set<String> blocked = union(global.getImageBlockList(), event == null ? null : event.blockedImages());
        return new Resolution(allowed, blocked);
    }

    private static Set<String> union(final String csvA, final String csvB) {
        final Set<String> result = new LinkedHashSet<>();
        addAll(result, csvA);
        addAll(result, csvB);
        return result;
    }

    private static void addAll(final Set<String> target, final String csv) {
        if (csv == null || csv.isBlank()) return;
        for (final String item : csv.split(",")) {
            final String trimmed = item.strip();
            if (!trimmed.isEmpty()) target.add(trimmed.toLowerCase());
        }
    }
}
