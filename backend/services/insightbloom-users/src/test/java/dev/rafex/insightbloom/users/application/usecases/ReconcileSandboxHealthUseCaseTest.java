package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.Conference;
import dev.rafex.insightbloom.users.domain.model.Sandbox;
import dev.rafex.insightbloom.users.domain.ports.ConferenceRepository;
import dev.rafex.insightbloom.users.domain.ports.SandboxOrchestrator;
import dev.rafex.insightbloom.users.domain.ports.SandboxRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ReconcileSandboxHealthUseCaseTest {
    @Test void recreatesFailedPodButLeavesImageFailureForGitOps() {
        var sandboxes=Mockito.mock(SandboxRepository.class); var conferences=Mockito.mock(ConferenceRepository.class);
        var orchestrator=Mockito.mock(SandboxOrchestrator.class); var conference=Mockito.mock(Conference.class);
        var now=Instant.now(); var failed=new Sandbox("conf",0,"user",now.plusSeconds(60));
        var image=new Sandbox("conf",1,"user",now.plusSeconds(60));
        Mockito.when(conference.getUuid()).thenReturn("conf"); Mockito.when(conferences.findAll()).thenReturn(List.of(conference));
        Mockito.when(sandboxes.findByConferenceUuid("conf")).thenReturn(List.of(failed,image));
        Mockito.when(orchestrator.getRuntimeStatus(failed.podName())).thenReturn(new SandboxOrchestrator.RuntimeStatus("Failed",false,"OOMKilled",1));
        Mockito.when(orchestrator.getRuntimeStatus(image.podName())).thenReturn(new SandboxOrchestrator.RuntimeStatus("Pending",false,"ErrImageNeverPull",0));
        Mockito.when(orchestrator.isImageCurrent(failed.podName(), failed.getVariant())).thenReturn(true);
        assertEquals(1,new ReconcileSandboxHealthUseCase(sandboxes,conferences,orchestrator).execute(now));
        Mockito.verify(orchestrator).deleteSandbox(failed.podName()); Mockito.verify(orchestrator,Mockito.never()).deleteSandbox(image.podName());
    }

    @Test void rotatesFreePodWhenGitOpsImageChanges() {
        var sandboxes=Mockito.mock(SandboxRepository.class); var conferences=Mockito.mock(ConferenceRepository.class);
        var orchestrator=Mockito.mock(SandboxOrchestrator.class); var conference=Mockito.mock(Conference.class);
        var now=Instant.now(); var free=new Sandbox("conf",2,null,now.plusSeconds(60));
        Mockito.when(conference.getUuid()).thenReturn("conf"); Mockito.when(conferences.findAll()).thenReturn(List.of(conference));
        Mockito.when(sandboxes.findByConferenceUuid("conf")).thenReturn(List.of(free));
        Mockito.when(orchestrator.getRuntimeStatus(free.podName())).thenReturn(new SandboxOrchestrator.RuntimeStatus("Running",true,null,0));
        Mockito.when(orchestrator.isImageCurrent(free.podName(), free.getVariant())).thenReturn(false);

        assertEquals(1, new ReconcileSandboxHealthUseCase(sandboxes, conferences, orchestrator).execute(now));

        Mockito.verify(orchestrator).deleteSandbox(free.podName());
        Mockito.verify(sandboxes).deletePod("conf", Sandbox.VARIANT_WEB, 2);
        Mockito.verify(sandboxes).save(free);
        Mockito.verify(orchestrator).createSandbox(free.podName(), "conf", "python", null, false, 0, 0);
    }

    @Test void keepsOccupiedPodWhenGitOpsImageChanges() {
        var sandboxes=Mockito.mock(SandboxRepository.class); var conferences=Mockito.mock(ConferenceRepository.class);
        var orchestrator=Mockito.mock(SandboxOrchestrator.class); var conference=Mockito.mock(Conference.class);
        var now=Instant.now(); var occupied=new Sandbox("conf",3,"user",now.plusSeconds(60));
        Mockito.when(conference.getUuid()).thenReturn("conf"); Mockito.when(conferences.findAll()).thenReturn(List.of(conference));
        Mockito.when(sandboxes.findByConferenceUuid("conf")).thenReturn(List.of(occupied));
        Mockito.when(orchestrator.getRuntimeStatus(occupied.podName())).thenReturn(new SandboxOrchestrator.RuntimeStatus("Running",true,null,0));
        Mockito.when(orchestrator.isImageCurrent(occupied.podName(), occupied.getVariant())).thenReturn(false);

        assertEquals(0, new ReconcileSandboxHealthUseCase(sandboxes, conferences, orchestrator).execute(now));
        Mockito.verify(orchestrator, Mockito.never()).deleteSandbox(occupied.podName());
        Mockito.verify(sandboxes, Mockito.never()).deletePod(Mockito.anyString(), Mockito.anyString(), Mockito.anyInt());
    }
}
