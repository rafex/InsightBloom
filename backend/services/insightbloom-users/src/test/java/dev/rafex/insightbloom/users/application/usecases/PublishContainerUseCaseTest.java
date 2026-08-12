package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.ContainerBuildResult;
import dev.rafex.insightbloom.users.domain.model.ImagePolicy;
import dev.rafex.insightbloom.users.domain.model.PlatformSettings;
import dev.rafex.insightbloom.users.domain.model.Sandbox;
import dev.rafex.insightbloom.users.domain.model.SandboxAppPreview;
import dev.rafex.insightbloom.users.domain.model.WorkspaceFileContent;
import dev.rafex.insightbloom.users.domain.ports.ImagePolicyRepository;
import dev.rafex.insightbloom.users.domain.ports.PlatformSettingsRepository;
import dev.rafex.insightbloom.users.domain.ports.SandboxAppPreviewRepository;
import dev.rafex.insightbloom.users.domain.ports.SandboxOrchestrator;
import dev.rafex.insightbloom.users.domain.ports.SandboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class PublishContainerUseCaseTest {
    private static final String SHARED_POD = "sandbox-runtime-podman-shared";
    private static final int APP_BASE_PORT = 9500;

    private SandboxRepository sandboxRepoMock;
    private SandboxOrchestrator orchestratorMock;
    private SandboxAppPreviewRepository previewRepoMock;
    private PlatformSettingsRepository platformSettingsRepoMock;
    private ImagePolicyRepository imagePolicyRepoMock;
    private PublishContainerUseCase useCase;
    private Sandbox ideSandbox;

    @BeforeEach
    void setup() {
        sandboxRepoMock = Mockito.mock(SandboxRepository.class);
        orchestratorMock = Mockito.mock(SandboxOrchestrator.class);
        previewRepoMock = Mockito.mock(SandboxAppPreviewRepository.class);
        platformSettingsRepoMock = Mockito.mock(PlatformSettingsRepository.class);
        imagePolicyRepoMock = Mockito.mock(ImagePolicyRepository.class);

        final var resolveImagePolicyUseCase =
                new ResolveImagePolicyUseCase(platformSettingsRepoMock, imagePolicyRepoMock);
        useCase = new PublishContainerUseCase(sandboxRepoMock, orchestratorMock, previewRepoMock,
                resolveImagePolicyUseCase, SHARED_POD, APP_BASE_PORT, 10);

        ideSandbox = new Sandbox("conf-1", 0, "user-1", Instant.now().plusSeconds(3600));

        final var platformSettings = new PlatformSettings();
        Mockito.when(platformSettingsRepoMock.get()).thenReturn(platformSettings);
        Mockito.when(imagePolicyRepoMock.findByConference("conf-1")).thenReturn(Optional.empty());
    }

    @Test
    void rejectsWhenNoIdeSandboxAssigned() {
        Mockito.when(sandboxRepoMock.findByConferenceAndUser("conf-1", "user-1")).thenReturn(Optional.empty());

        final var ex = assertThrows(IllegalArgumentException.class,
                () -> useCase.execute("conf-1", "user-1", "Containerfile", 3600));
        assertEquals("sandbox_not_assigned", ex.getMessage());
        Mockito.verifyNoInteractions(orchestratorMock);
    }

    @Test
    void rejectsContainerfileWithDisallowedImage() {
        Mockito.when(sandboxRepoMock.findByConferenceAndUser("conf-1", "user-1")).thenReturn(Optional.of(ideSandbox));
        Mockito.when(orchestratorMock.readWorkspaceFile(ideSandbox.podName(), 0, "Containerfile"))
                .thenReturn(new WorkspaceFileContent("FROM quay.io/some/image\n", 1.0));

        final var ex = assertThrows(PublishContainerUseCase.ContainerValidationException.class,
                () -> useCase.execute("conf-1", "user-1", "Containerfile", 3600));
        assertEquals("containerfile_registry_not_allowed", ex.errorCode());
        Mockito.verify(orchestratorMock, Mockito.never()).ensureRuntimePodmanPod(Mockito.anyString());
    }

    @Test
    void publishesContainerWithExposedPortAndCreatesPreview() {
        Mockito.when(sandboxRepoMock.findByConferenceAndUser("conf-1", "user-1")).thenReturn(Optional.of(ideSandbox));
        Mockito.when(orchestratorMock.readWorkspaceFile(ideSandbox.podName(), 0, "Containerfile"))
                .thenReturn(new WorkspaceFileContent("FROM python:3.12\nEXPOSE 8000\n", 1.0));
        Mockito.when(orchestratorMock.buildAndRunContainer(
                        Mockito.eq(SHARED_POD), Mockito.anyString(), Mockito.eq(APP_BASE_PORT), Mockito.eq(8000)))
                .thenReturn(ContainerBuildResult.ok());
        Mockito.when(previewRepoMock.findByConferenceAndUser("conf-1", "user-1")).thenReturn(Optional.empty());
        Mockito.when(previewRepoMock.findActiveByPodName(SHARED_POD)).thenReturn(List.of());
        Mockito.when(previewRepoMock.save(Mockito.any(SandboxAppPreview.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        final var result = useCase.execute("conf-1", "user-1", "Containerfile", 3600);

        assertTrue(result.published());
        assertEquals(APP_BASE_PORT, result.preview().targetPort());
        assertEquals(SHARED_POD, result.preview().podName());
        Mockito.verify(orchestratorMock).ensureRuntimePodmanPod(SHARED_POD);
    }

    @Test
    void buildsWithoutPreviewWhenNoExposeDeclared() {
        Mockito.when(sandboxRepoMock.findByConferenceAndUser("conf-1", "user-1")).thenReturn(Optional.of(ideSandbox));
        Mockito.when(orchestratorMock.readWorkspaceFile(ideSandbox.podName(), 0, "Containerfile"))
                .thenReturn(new WorkspaceFileContent("FROM python:3.12\n", 1.0));
        Mockito.when(orchestratorMock.buildAndRunContainer(
                        Mockito.eq(SHARED_POD), Mockito.anyString(), Mockito.anyInt(), Mockito.eq(-1)))
                .thenReturn(ContainerBuildResult.ok());
        Mockito.when(previewRepoMock.findByConferenceAndUser("conf-1", "user-1")).thenReturn(Optional.empty());
        Mockito.when(previewRepoMock.findActiveByPodName(SHARED_POD)).thenReturn(List.of());

        final var result = useCase.execute("conf-1", "user-1", "Containerfile", 3600);

        assertFalse(result.published());
        assertNull(result.preview());
        Mockito.verify(previewRepoMock, Mockito.never()).save(Mockito.any());
    }

    @Test
    void clampsTtlToOneHourMaxRegardlessOfRequestedValue() {
        Mockito.when(sandboxRepoMock.findByConferenceAndUser("conf-1", "user-1")).thenReturn(Optional.of(ideSandbox));
        Mockito.when(orchestratorMock.readWorkspaceFile(ideSandbox.podName(), 0, "Containerfile"))
                .thenReturn(new WorkspaceFileContent("FROM python:3.12\nEXPOSE 8000\n", 1.0));
        Mockito.when(orchestratorMock.buildAndRunContainer(
                        Mockito.eq(SHARED_POD), Mockito.anyString(), Mockito.eq(APP_BASE_PORT), Mockito.eq(8000)))
                .thenReturn(ContainerBuildResult.ok());
        Mockito.when(previewRepoMock.findByConferenceAndUser("conf-1", "user-1")).thenReturn(Optional.empty());
        Mockito.when(previewRepoMock.findActiveByPodName(SHARED_POD)).thenReturn(List.of());
        Mockito.when(previewRepoMock.save(Mockito.any(SandboxAppPreview.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        final var result = useCase.execute("conf-1", "user-1", "Containerfile", 24 * 3600);

        assertTrue(result.preview().expiresAt().isBefore(Instant.now().plusSeconds(3700)));
    }

    @Test
    void reusesOwnPortWhenRepublishing() {
        Mockito.when(sandboxRepoMock.findByConferenceAndUser("conf-1", "user-1")).thenReturn(Optional.of(ideSandbox));
        Mockito.when(orchestratorMock.readWorkspaceFile(ideSandbox.podName(), 0, "Containerfile"))
                .thenReturn(new WorkspaceFileContent("FROM python:3.12\nEXPOSE 8000\n", 1.0));
        Mockito.when(orchestratorMock.buildAndRunContainer(
                        Mockito.eq(SHARED_POD), Mockito.anyString(), Mockito.eq(APP_BASE_PORT + 3), Mockito.eq(8000)))
                .thenReturn(ContainerBuildResult.ok());
        final var existingOwn = new SandboxAppPreview("pub-1", "conf-1", "user-1", SHARED_POD, APP_BASE_PORT + 3,
                "tok", Instant.now(), Instant.now().plusSeconds(3600));
        Mockito.when(previewRepoMock.findByConferenceAndUser("conf-1", "user-1")).thenReturn(Optional.of(existingOwn));
        Mockito.when(previewRepoMock.save(Mockito.any(SandboxAppPreview.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        final var result = useCase.execute("conf-1", "user-1", "Containerfile", 3600);

        assertEquals(APP_BASE_PORT + 3, result.preview().targetPort());
        Mockito.verify(previewRepoMock, Mockito.never()).findActiveByPodName(Mockito.anyString());
    }

    @Test
    void rejectsWhenSharedPodPortsAllTaken() {
        Mockito.when(sandboxRepoMock.findByConferenceAndUser("conf-1", "user-1")).thenReturn(Optional.of(ideSandbox));
        Mockito.when(orchestratorMock.readWorkspaceFile(ideSandbox.podName(), 0, "Containerfile"))
                .thenReturn(new WorkspaceFileContent("FROM python:3.12\nEXPOSE 8000\n", 1.0));
        Mockito.when(previewRepoMock.findByConferenceAndUser("conf-1", "user-1")).thenReturn(Optional.empty());
        final List<SandboxAppPreview> allTaken = new java.util.ArrayList<>();
        for (int i = 0; i < 10; i++) {
            allTaken.add(new SandboxAppPreview("pub-" + i, "other-conf", "other-user-" + i, SHARED_POD,
                    APP_BASE_PORT + i, "tok", Instant.now(), Instant.now().plusSeconds(3600)));
        }
        Mockito.when(previewRepoMock.findActiveByPodName(SHARED_POD)).thenReturn(allTaken);

        final var ex = assertThrows(IllegalArgumentException.class,
                () -> useCase.execute("conf-1", "user-1", "Containerfile", 3600));
        assertEquals("publish_container_pool_full", ex.getMessage());
        Mockito.verify(orchestratorMock, Mockito.never()).buildAndRunContainer(
                Mockito.anyString(), Mockito.anyString(), Mockito.anyInt(), Mockito.anyInt());
    }
}
