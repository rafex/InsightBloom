package dev.rafex.insightbloom.users.domain.ports;

import dev.rafex.insightbloom.users.domain.model.EgressPolicy;

import java.util.Optional;

public interface EgressPolicyRepository {
    Optional<EgressPolicy> findByConference(String conferenceUuid);

    EgressPolicy save(EgressPolicy policy);

    void deleteByConference(String conferenceUuid);
}
