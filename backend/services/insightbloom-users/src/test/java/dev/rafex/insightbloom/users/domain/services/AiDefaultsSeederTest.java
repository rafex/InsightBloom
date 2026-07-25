package dev.rafex.insightbloom.users.domain.services;

import dev.rafex.insightbloom.users.domain.model.PlatformSettings;
import dev.rafex.insightbloom.users.domain.ports.PlatformSettingsRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AiDefaultsSeederTest {

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
        AiDefaultsSeeder.seedIfNeeded(repo, false);
        assertNull(repo.get().getTutorAi().getSystemPrompt());
        assertFalse(repo.get().getTutorAi().isConfigured());
        assertEquals(0, repo.saveCount);
    }

    @Test
    void seedsUnconfiguredCapabilitiesWithRealPromptsAndGuardrails() {
        final InMemoryRepo repo = new InMemoryRepo();
        AiDefaultsSeeder.seedIfNeeded(repo, true);

        assertTrue(repo.get().getTutorAi().isConfigured());
        assertNotNull(repo.get().getTutorAi().getSystemPrompt());
        assertFalse(repo.get().getTutorAi().getSystemPrompt().isBlank());
        assertNotNull(repo.get().getTutorAi().getGuardrails());

        assertTrue(repo.get().getSurveyAi().isConfigured());
        assertTrue(repo.get().getSeatLayoutAi().isConfigured());

        assertNotNull(repo.get().getChatAi().getSystemPrompt());
        assertNotNull(repo.get().getChatAi().getGuardrails());
        assertEquals(1, repo.saveCount);
    }

    @Test
    void neverOverwritesAlreadyConfiguredCapability() {
        final InMemoryRepo repo = new InMemoryRepo();
        final PlatformSettings settings = repo.get();
        settings.getTutorAi().setConfigured(true);
        settings.getTutorAi().setSystemPrompt("prompt del admin, no tocar");
        repo.save(settings);

        AiDefaultsSeeder.seedIfNeeded(repo, true);

        assertEquals("prompt del admin, no tocar", repo.get().getTutorAi().getSystemPrompt());
    }

    @Test
    void isIdempotentAcrossMultipleBoots() {
        final InMemoryRepo repo = new InMemoryRepo();
        AiDefaultsSeeder.seedIfNeeded(repo, true);
        final String firstPrompt = repo.get().getTutorAi().getSystemPrompt();

        AiDefaultsSeeder.seedIfNeeded(repo, true);

        assertEquals(firstPrompt, repo.get().getTutorAi().getSystemPrompt());
        assertEquals(1, repo.saveCount);
    }
}
