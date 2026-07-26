package dev.rafex.insightbloom.users.domain.services;

import dev.rafex.insightbloom.users.domain.model.PlatformSettings;
import dev.rafex.insightbloom.users.domain.ports.PlatformSettingsRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EgressPolicyDefaultsSeederTest {

    private static final class InMemoryRepo implements PlatformSettingsRepository {
        private PlatformSettings settings = PlatformSettings.defaults();
        private int saveCount = 0;

        @Override
        public PlatformSettings get() { return settings; }

        @Override
        public void save(final PlatformSettings s) { this.settings = s; saveCount++; }
    }

    @Test
    void doesNothingWhenDisabled() {
        final InMemoryRepo repo = new InMemoryRepo();
        EgressPolicyDefaultsSeeder.seedIfNeeded(repo, false, "github.com", "localhost");
        assertNull(repo.get().getEgressAllowedHosts());
        assertEquals(0, repo.saveCount);
    }

    @Test
    void seedsFromDefaultsWhenNothingConfiguredYet() {
        final InMemoryRepo repo = new InMemoryRepo();
        EgressPolicyDefaultsSeeder.seedIfNeeded(repo, true, "github.com,*.npmjs.org", "localhost,169.254.169.254");

        assertEquals("github.com,*.npmjs.org", repo.get().getEgressAllowedHosts());
        assertEquals("localhost,169.254.169.254", repo.get().getEgressBlockedHosts());
        assertEquals(1, repo.saveCount);
    }

    @Test
    void neverOverwritesAlreadyConfiguredPolicy() {
        final InMemoryRepo repo = new InMemoryRepo();
        final PlatformSettings settings = repo.get();
        settings.setEgressAllowedHosts("admin-set.example.com");
        settings.setEgressBlockedHosts("");
        repo.save(settings);

        EgressPolicyDefaultsSeeder.seedIfNeeded(repo, true, "github.com", "localhost");

        assertEquals("admin-set.example.com", repo.get().getEgressAllowedHosts());
        assertEquals(1, repo.saveCount); // solo el save explicito de arriba, el seeder no volvio a guardar
    }

    @Test
    void isIdempotentAcrossMultipleBoots() {
        final InMemoryRepo repo = new InMemoryRepo();
        EgressPolicyDefaultsSeeder.seedIfNeeded(repo, true, "github.com", "localhost");
        EgressPolicyDefaultsSeeder.seedIfNeeded(repo, true, "github.com", "localhost");

        assertEquals("github.com", repo.get().getEgressAllowedHosts());
        assertEquals(1, repo.saveCount);
    }
}
