package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.ImagePolicy;
import dev.rafex.insightbloom.users.domain.model.PlatformSettings;
import dev.rafex.insightbloom.users.domain.ports.ImagePolicyRepository;
import dev.rafex.insightbloom.users.domain.ports.PlatformSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ResolveImagePolicyUseCaseTest {
    private static final String CONF = "conf-1";

    private PlatformSettingsRepository platformSettingsRepository;
    private ImagePolicyRepository imagePolicyRepository;
    private ResolveImagePolicyUseCase useCase;

    @BeforeEach
    void setUp() {
        platformSettingsRepository = Mockito.mock(PlatformSettingsRepository.class);
        imagePolicyRepository = Mockito.mock(ImagePolicyRepository.class);
        useCase = new ResolveImagePolicyUseCase(platformSettingsRepository, imagePolicyRepository);
    }

    @Test
    void unionsGlobalAndEventAllowedAndBlockedLists() {
        final PlatformSettings global = PlatformSettings.defaults();
        global.setImageAllowList("python,node");
        global.setImageBlockList("alpine");
        Mockito.when(platformSettingsRepository.get()).thenReturn(global);
        Mockito.when(imagePolicyRepository.findByConference(CONF)).thenReturn(Optional.of(
                new ImagePolicy(CONF, "golang", "ubuntu", Instant.now())));

        final var resolution = useCase.execute(CONF);

        assertEquals(Set.of("python", "node", "golang"), resolution.allowed());
        assertEquals(Set.of("alpine", "ubuntu"), resolution.blocked());
    }

    @Test
    void usesOnlyGlobalWhenNoEventPolicyExists() {
        final PlatformSettings global = PlatformSettings.defaults();
        global.setImageAllowList("python");
        Mockito.when(platformSettingsRepository.get()).thenReturn(global);
        Mockito.when(imagePolicyRepository.findByConference(CONF)).thenReturn(Optional.empty());

        final var resolution = useCase.execute(CONF);

        assertEquals(Set.of("python"), resolution.allowed());
        assertTrue(resolution.blocked().isEmpty());
    }

    @Test
    void blockedAlwaysWinsOverAllowed() {
        final PlatformSettings global = PlatformSettings.defaults();
        global.setImageAllowList("python");
        global.setImageBlockList("python");
        Mockito.when(platformSettingsRepository.get()).thenReturn(global);
        Mockito.when(imagePolicyRepository.findByConference(CONF)).thenReturn(Optional.empty());

        final var resolution = useCase.execute(CONF);

        assertFalse(resolution.isAllowed("python:3.12"));
    }

    @Test
    void eventCannotUnblockWhatGlobalForbids() {
        final PlatformSettings global = PlatformSettings.defaults();
        global.setImageBlockList("python");
        Mockito.when(platformSettingsRepository.get()).thenReturn(global);
        Mockito.when(imagePolicyRepository.findByConference(CONF)).thenReturn(Optional.of(
                new ImagePolicy(CONF, "python", null, Instant.now())));

        final var resolution = useCase.execute(CONF);

        assertFalse(resolution.isAllowed("python"));
    }

    @Test
    void emptyAllowlistMeansNoWhitelistRestriction() {
        final PlatformSettings global = PlatformSettings.defaults();
        Mockito.when(platformSettingsRepository.get()).thenReturn(global);
        Mockito.when(imagePolicyRepository.findByConference(CONF)).thenReturn(Optional.empty());

        final var resolution = useCase.execute(CONF);

        assertTrue(resolution.isAllowed("anything"));
    }

    @Test
    void isAllowedMatchesByPrefix() {
        final PlatformSettings global = PlatformSettings.defaults();
        global.setImageAllowList("python");
        Mockito.when(platformSettingsRepository.get()).thenReturn(global);
        Mockito.when(imagePolicyRepository.findByConference(CONF)).thenReturn(Optional.empty());

        final var resolution = useCase.execute(CONF);

        assertTrue(resolution.isAllowed("python-slim"));
        assertFalse(resolution.isAllowed("node"));
    }
}
