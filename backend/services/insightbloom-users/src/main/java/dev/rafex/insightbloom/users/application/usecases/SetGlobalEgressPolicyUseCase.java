package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.PlatformSettings;
import dev.rafex.insightbloom.users.domain.ports.PlatformSettingsRepository;

/** Lista blanca/negra GLOBAL de egress, configurable desde /dashboard/admin/egress-policy. */
public class SetGlobalEgressPolicyUseCase {
    private static final int MAX_HOSTS_TEXT_LENGTH = 20_000;

    private final PlatformSettingsRepository repository;

    public SetGlobalEgressPolicyUseCase(final PlatformSettingsRepository repository) {
        this.repository = repository;
    }

    public PlatformSettings execute(final String allowedHosts, final String blockedHosts) {
        if (allowedHosts != null && allowedHosts.length() > MAX_HOSTS_TEXT_LENGTH) {
            throw new IllegalArgumentException("allowed_hosts_too_long");
        }
        if (blockedHosts != null && blockedHosts.length() > MAX_HOSTS_TEXT_LENGTH) {
            throw new IllegalArgumentException("blocked_hosts_too_long");
        }
        final PlatformSettings s = repository.get();
        s.setEgressAllowedHosts(allowedHosts);
        s.setEgressBlockedHosts(blockedHosts);
        repository.save(s);
        return s;
    }
}
