package dev.rafex.insightbloom.users.domain.model;

import java.time.Instant;

/**
 * Lista blanca/negra de imágenes base de contenedor que un evento puede publicar (ver
 * PublishContainerUseCase, ContainerfileValidator) -- se suma a la lista global de
 * {@link PlatformSettings#getImageAllowList()}/{@link PlatformSettings#getImageBlockList()}
 * (herencia en capas, mismo criterio que {@link EgressPolicy}/ResolveEgressPolicyUseCase):
 * allowed = global ∪ evento, blocked = global ∪ evento, y blocked SIEMPRE gana. Cada entrada es
 * un prefijo/patrón simple sobre el nombre de imagen sin tag (ej. "python", "node") -- no hace
 * falta listar cada tag exacto.
 */
public record ImagePolicy(
        String conferenceUuid,
        String allowedImages,
        String blockedImages,
        Instant updatedAt) {
}
