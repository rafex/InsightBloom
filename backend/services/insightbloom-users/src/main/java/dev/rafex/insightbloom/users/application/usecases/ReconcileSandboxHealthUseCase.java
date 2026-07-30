package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.Conference;
import dev.rafex.insightbloom.users.domain.model.Sandbox;
import dev.rafex.insightbloom.users.domain.ports.ConferenceRepository;
import dev.rafex.insightbloom.users.domain.ports.SandboxOrchestrator;
import dev.rafex.insightbloom.users.domain.ports.SandboxRepository;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/** Recupera Pods perdidos o terminales; los fallos de imagen se dejan para GitOps. */
public class ReconcileSandboxHealthUseCase {
    private final SandboxRepository sandboxes; private final ConferenceRepository conferences;
    private final SandboxOrchestrator orchestrator;
    public ReconcileSandboxHealthUseCase(SandboxRepository s, ConferenceRepository c, SandboxOrchestrator o) {
        sandboxes=s; conferences=c; orchestrator=o;
    }
    public int execute(Instant now) {
        int recovered=0; Set<String> seen=new HashSet<>();
        for (Conference c: conferences.findAll()) for (Sandbox s: sandboxes.findByConferenceUuid(c.getUuid())) {
            if (!seen.add(s.podName()) || s.getExpiresAt().isBefore(now)) continue;
            var state=orchestrator.getRuntimeStatus(s.podName());
            String reason=state.reason()==null?"":state.reason();
            boolean imageFailure=reason.contains("ImagePull") || reason.contains("ErrImage");
            boolean terminal=state.phase()==null || "Failed".equals(state.phase()) || "Succeeded".equals(state.phase());
            if (!terminal || imageFailure) continue;
            orchestrator.deleteSandbox(s.podName());
            boolean internet=c.getSandboxInternetEnabled()!=null && c.getSandboxInternetEnabled()==1;
            orchestrator.createSandbox(s.podName(), c.getUuid(), Sandbox.VARIANT_CLI.equals(s.getVariant()) ? "terminal-nvim" : "python",
                c.getSandboxRemoteGitUrl(), internet, c.getSandboxJvmHeapMb(), c.getSandboxSeatsPerPod());
            recovered++;
        }
        return recovered;
    }
}
