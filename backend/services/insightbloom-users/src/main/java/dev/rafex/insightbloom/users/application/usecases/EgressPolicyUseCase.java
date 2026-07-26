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
                new EgressPolicy(conferenceUuid, null, null, Instant.now()));
    }

    public EgressPolicy save(final String conferenceUuid, final String allowedHosts, final String blockedHosts) {
        if (conferenceUuid == null || conferenceUuid.isBlank()) throw new IllegalArgumentException("conference_required");
        if (allowedHosts != null && allowedHosts.length() > MAX_HOSTS_TEXT_LENGTH) {
            throw new IllegalArgumentException("allowed_hosts_too_long");
        }
        if (blockedHosts != null && blockedHosts.length() > MAX_HOSTS_TEXT_LENGTH) {
            throw new IllegalArgumentException("blocked_hosts_too_long");
        }
        return repository.save(new EgressPolicy(conferenceUuid, allowedHosts, blockedHosts, Instant.now()));
    }
}
