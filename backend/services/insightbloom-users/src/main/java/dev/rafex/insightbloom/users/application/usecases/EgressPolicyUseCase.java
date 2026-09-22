package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.EgressPolicy;
import dev.rafex.insightbloom.users.domain.ports.EgressPolicyRepository;

import java.time.Instant;

/** Lista blanca/negra de egress POR EVENTO -- se suma a la global (ver ResolveEgressPolicyUseCase). */
public class EgressPolicyUseCase {
    private static final int MAX_HOSTS_TEXT_LENGTH = 20_000;

    private final EgressPolicyRepository repository;

    public EgressPolicyUseCase(final EgressPolicyRepository repository) {
        this.repository = repository;
    }

    public EgressPolicy get(final String conferenceUuid) {
        return repository.findByConference(conferenceUuid).orElseGet(() ->
                new EgressPolicy(conferenceUuid, null, null, false, Instant.now()));
    }

    public EgressPolicy save(final String conferenceUuid, final String allowedHosts, final String blockedHosts) {
        return save(conferenceUuid, allowedHosts, blockedHosts, null);
    }

    /**
     * A {@code null} allowAll preserves the stored value so older API clients cannot accidentally
     * revoke an event owner's explicit exception while updating host lists.
     */
    public EgressPolicy save(final String conferenceUuid, final String allowedHosts, final String blockedHosts,
                             final Boolean allowAll) {
        if (conferenceUuid == null || conferenceUuid.isBlank()) throw new IllegalArgumentException("conference_required");
        if (allowedHosts != null && allowedHosts.length() > MAX_HOSTS_TEXT_LENGTH) {
            throw new IllegalArgumentException("allowed_hosts_too_long");
        }
        if (blockedHosts != null && blockedHosts.length() > MAX_HOSTS_TEXT_LENGTH) {
            throw new IllegalArgumentException("blocked_hosts_too_long");
        }
        final boolean effectiveAllowAll = allowAll != null
                ? allowAll
                : repository.findByConference(conferenceUuid).map(EgressPolicy::allowAll).orElse(false);
        return repository.save(new EgressPolicy(conferenceUuid, allowedHosts, blockedHosts, effectiveAllowAll,
                Instant.now()));
    }
}
