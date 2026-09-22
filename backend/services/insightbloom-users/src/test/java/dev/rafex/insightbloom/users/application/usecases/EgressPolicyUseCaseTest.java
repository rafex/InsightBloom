package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.EgressPolicy;
import dev.rafex.insightbloom.users.domain.ports.EgressPolicyRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EgressPolicyUseCaseTest {

    @Test
    void preservesAllowAllWhenAnOlderClientOmitsIt() {
        final EgressPolicy existing = new EgressPolicy("conf-1", "before.example", null, true, Instant.now());
        final EgressPolicy[] saved = new EgressPolicy[1];
        final EgressPolicyRepository repository = new EgressPolicyRepository() {
            @Override public Optional<EgressPolicy> findByConference(final String conferenceUuid) {
                return Optional.of(existing);
            }
            @Override public EgressPolicy save(final EgressPolicy policy) { saved[0] = policy; return policy; }
            @Override public void deleteByConference(final String conferenceUuid) { }
        };

        new EgressPolicyUseCase(repository).save("conf-1", "after.example", null);

        assertTrue(saved[0].allowAll());
    }

    @Test
    void defaultsAllowAllToFalseForANewPolicy() {
        final EgressPolicy[] saved = new EgressPolicy[1];
        final EgressPolicyRepository repository = new EgressPolicyRepository() {
            @Override public Optional<EgressPolicy> findByConference(final String conferenceUuid) {
                return Optional.empty();
            }
            @Override public EgressPolicy save(final EgressPolicy policy) { saved[0] = policy; return policy; }
            @Override public void deleteByConference(final String conferenceUuid) { }
        };

        new EgressPolicyUseCase(repository).save("conf-1", null, null);

        assertFalse(saved[0].allowAll());
    }
}
