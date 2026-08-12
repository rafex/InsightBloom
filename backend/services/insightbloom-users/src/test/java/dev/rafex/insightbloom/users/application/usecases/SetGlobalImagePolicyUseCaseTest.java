package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.PlatformSettings;
import dev.rafex.insightbloom.users.domain.ports.PlatformSettingsRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SetGlobalImagePolicyUseCaseTest {
    @Test
    void savesAllowedAndBlockedLists() {
        final PlatformSettingsRepository repository = mock(PlatformSettingsRepository.class);
        final PlatformSettings settings = PlatformSettings.defaults();
        when(repository.get()).thenReturn(settings);

        final PlatformSettings result = new SetGlobalImagePolicyUseCase(repository).execute("python,node", "alpine");

        assertEquals("python,node", result.getImageAllowList());
        assertEquals("alpine", result.getImageBlockList());
        verify(repository).save(settings);
    }

    @Test
    void rejectsOverlyLongLists() {
        final PlatformSettingsRepository repository = mock(PlatformSettingsRepository.class);
        when(repository.get()).thenReturn(PlatformSettings.defaults());
        final var useCase = new SetGlobalImagePolicyUseCase(repository);
        final String tooLong = "a".repeat(20_001);
        assertThrows(IllegalArgumentException.class, () -> useCase.execute(tooLong, null));
        assertThrows(IllegalArgumentException.class, () -> useCase.execute(null, tooLong));
    }
}
