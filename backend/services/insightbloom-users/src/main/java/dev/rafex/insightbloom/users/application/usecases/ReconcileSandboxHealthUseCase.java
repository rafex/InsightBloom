package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.Conference;
import dev.rafex.insightbloom.users.domain.model.Sandbox;
import dev.rafex.insightbloom.users.domain.ports.ConferenceRepository;
import dev.rafex.insightbloom.users.domain.ports.SandboxOrchestrator;
import dev.rafex.insightbloom.users.domain.ports.SandboxRepository;
import java.time.Instant;
import java.util.List;
import java.util.HashSet;
import java.util.Set;

/**
 * Recupera Pods perdidos o terminales y rota slots libres que todavía usan una imagen anterior.
 * Los Pods ocupados no se destruyen automáticamente: su {@code emptyDir} contiene el workspace
 * del alumno y debe conservarse hasta que la sesión expire o el moderador lo reinicie.
 */
public class ReconcileSandboxHealthUseCase {
    private final SandboxRepository sandboxes; private final ConferenceRepository conferences;
    private final SandboxOrchestrator orchestrator;
    public ReconcileSandboxHealthUseCase(SandboxRepository s, ConferenceRepository c, SandboxOrchestrator o) {
        sandboxes=s; conferences=c; orchestrator=o;
    }
    public int execute(Instant now) {
        int recovered=0; Set<String> seen=new HashSet<>();
        for (Conference c: conferences.findAll()) {
            final List<Sandbox> conferenceSandboxes = sandboxes.findByConferenceUuid(c.getUuid());
            for (Sandbox s: conferenceSandboxes) {
            if (!seen.add(s.podName()) || s.getExpiresAt().isBefore(now)) continue;
            var state=orchestrator.getRuntimeStatus(s.podName());
            String reason=state.reason()==null?"":state.reason();
            boolean imageFailure=reason.contains("ImagePull") || reason.contains("ErrImage");
            if (imageFailure) continue;

            if (state.phase() != null && !orchestrator.isImageCurrent(s.podName(), s.getVariant())) {
                if (rotateFreePod(c, conferenceSandboxes, s)) recovered++;
                continue;
            }

            boolean terminal=state.phase()==null || "Failed".equals(state.phase()) || "Succeeded".equals(state.phase());
            if (!terminal) continue;

            orchestrator.deleteSandbox(s.podName());
            boolean internet=c.getSandboxInternetEnabled()!=null && c.getSandboxInternetEnabled()==1;
            orchestrator.createSandbox(s.podName(), c.getUuid(), Sandbox.VARIANT_CLI.equals(s.getVariant()) ? "terminal-nvim" : "python",
                c.getSandboxRemoteGitUrl(), internet, c.getSandboxJvmHeapMb(), c.getSandboxSeatsPerPod());
            recovered++;
            }
        }
        return recovered;
    }

    private boolean rotateFreePod(final Conference conference, final List<Sandbox> conferenceSandboxes,
                                  final Sandbox representative) {
        final List<Sandbox> samePod = conferenceSandboxes.stream()
            .filter(s -> representative.podName().equals(s.podName()))
            .toList();
        if (samePod.stream().anyMatch(s -> s.getUserUuid() != null)) {
            // Nunca destruir un workspace activo solo por una actualización de imagen.
            return false;
        }

        orchestrator.deleteSandbox(representative.podName());
        sandboxes.deletePod(conference.getUuid(), representative.getVariant(), representative.getSandboxSlot());
        for (Sandbox sandbox : samePod) {
            sandboxes.save(sandbox);
        }
        final boolean internet=conference.getSandboxInternetEnabled()!=null && conference.getSandboxInternetEnabled()==1;
        orchestrator.createSandbox(representative.podName(), conference.getUuid(),
            Sandbox.VARIANT_CLI.equals(representative.getVariant()) ? "terminal-nvim" : "python",
            conference.getSandboxRemoteGitUrl(), internet, conference.getSandboxJvmHeapMb(),
            conference.getSandboxSeatsPerPod());
        return true;
    }
}
