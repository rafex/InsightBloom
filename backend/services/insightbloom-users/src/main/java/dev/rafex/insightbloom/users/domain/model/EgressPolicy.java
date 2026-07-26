package dev.rafex.insightbloom.users.domain.model;

import java.time.Instant;

/**
 * Lista blanca/negra de dominios que el egress-proxy puede alcanzar para los sandboxes (IDE
 * Web/CLI) de UN evento -- se suma a {@link PlatformSettings#getEgressAllowedHosts()}/
 * {@link PlatformSettings#getEgressBlockedHosts()} (herencia en capas, ver ResolveEgressPolicyUseCase):
 * allowed = global ∪ evento, blocked = global ∪ evento, y blocked SIEMPRE gana sobre allowed en
 * cualquier nivel. Formato de cada entrada: mismo que ya usa el proxy (dominio exacto o
 * {@code *.dominio} para subdominios).
 */
public record EgressPolicy(
        String conferenceUuid,
        String allowedHosts,
        String blockedHosts,
        Instant updatedAt) {
}
