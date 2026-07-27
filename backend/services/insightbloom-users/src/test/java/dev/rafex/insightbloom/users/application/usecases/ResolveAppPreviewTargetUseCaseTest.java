package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.SandboxAppPreview;
import dev.rafex.insightbloom.users.domain.ports.SandboxAppPreviewRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ResolveAppPreviewTargetUseCaseTest {

    private static final class InMemoryRepo implements SandboxAppPreviewRepository {
        private SandboxAppPreview stored;

        @Override
        public Optional<SandboxAppPreview> findByUuid(final String uuid) {
            return stored != null && stored.uuid().equals(uuid) ? Optional.of(stored) : Optional.empty();
        }

        @Override
        public Optional<SandboxAppPreview> findByConferenceAndUser(final String c, final String u) {
            return Optional.empty();
        }

        @Override
        public SandboxAppPreview save(final SandboxAppPreview preview) {
            this.stored = preview;
            return preview;
        }

        @Override
        public void deleteByUuid(final String uuid) {
            this.stored = null;
        }
    }

    private static SandboxAppPreview preview(final String token, final Instant expiresAt) {
        return new SandboxAppPreview("pub-1", "conf-1", "user-1", "sandbox-conf1-web-0",
                9000, token, Instant.now(), expiresAt);
    }

    @Test
    void resolvesTargetWhenTokenMatchesAndNotExpired() {
        final InMemoryRepo repo = new InMemoryRepo();
        repo.save(preview("correct-token", Instant.now().plusSeconds(3600)));
        final var useCase = new ResolveAppPreviewTargetUseCase(repo, "insightbloom-sandboxes");

        final var target = useCase.execute("pub-1", "correct-token");

        assertTrue(target.isPresent());
        assertEquals("http://sandbox-conf1-web-0-svc.insightbloom-sandboxes.svc.cluster.local:9000", target.get());
    }

    @Test
    void returnsEmptyWhenTokenDoesNotMatch() {
        final InMemoryRepo repo = new InMemoryRepo();
        repo.save(preview("correct-token", Instant.now().plusSeconds(3600)));
        final var useCase = new ResolveAppPreviewTargetUseCase(repo, "insightbloom-sandboxes");

        assertTrue(useCase.execute("pub-1", "wrong-token").isEmpty());
    }

    @Test
    void returnsEmptyWhenExpired() {
        final InMemoryRepo repo = new InMemoryRepo();
        repo.save(preview("correct-token", Instant.now().minusSeconds(10)));
        final var useCase = new ResolveAppPreviewTargetUseCase(repo, "insightbloom-sandboxes");

        assertTrue(useCase.execute("pub-1", "correct-token").isEmpty());
    }

    @Test
    void returnsEmptyWhenPublicationUnknown() {
        final InMemoryRepo repo = new InMemoryRepo();
        final var useCase = new ResolveAppPreviewTargetUseCase(repo, "insightbloom-sandboxes");

        assertTrue(useCase.execute("does-not-exist", "any-token").isEmpty());
    }
}
