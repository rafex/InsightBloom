package dev.rafex.insightbloom.users.domain.services;

import dev.rafex.insightbloom.users.domain.model.PlatformSettings;
import dev.rafex.insightbloom.users.domain.ports.PlatformSettingsRepository;

/**
 * Inicializa, una sola vez, la lista blanca/negra GLOBAL de egress con los mismos valores que
 * hoy vive en el ConfigMap ({@code EGRESS_PROXY_ALLOWED_HOSTS}/{@code _BLOCKED_HOSTS}) -- mismo
 * espíritu que {@link AiDefaultsSeeder}: mover la fuente de verdad a la base de datos (editable
 * desde el dashboard, sin pasar por gitops/Flux) sin perder el punto de partida actual.
 *
 * Solo siembra si {@code platform_settings.egress_allowed_hosts} todavía está vacío -- nunca
 * sobreescribe lo que un admin ya haya guardado desde el dashboard. Idempotente: correr esto en
 * cada boot es seguro.
 *
 * Gateado por la env var EGRESS_POLICY_SEED_DEFAULTS (default "true" -- ver UsersApplication).
 */
public final class EgressPolicyDefaultsSeeder {
    private EgressPolicyDefaultsSeeder() {}

    public static void seedIfNeeded(final PlatformSettingsRepository repository, final boolean enabled,
                                     final String defaultAllowedHosts, final String defaultBlockedHosts) {
        if (!enabled) return;
        final PlatformSettings settings = repository.get();
        if (settings.getEgressAllowedHosts() != null && !settings.getEgressAllowedHosts().isBlank()) {
            return; // ya sembrado o ya editado por un admin -- nunca se pisa.
        }
        settings.setEgressAllowedHosts(defaultAllowedHosts == null ? "" : defaultAllowedHosts);
        settings.setEgressBlockedHosts(defaultBlockedHosts == null ? "" : defaultBlockedHosts);
        repository.save(settings);
    }
}
