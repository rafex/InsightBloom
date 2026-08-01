package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.Sandbox;
import dev.rafex.insightbloom.users.domain.ports.SandboxOrchestrator;
import dev.rafex.insightbloom.users.domain.ports.SandboxRepository;
import dev.rafex.insightbloom.users.domain.ports.UserRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Estado en vivo de los Pods de sandbox de una conferencia, para el dashboard del moderador
 * ("estado de las maquinas"). Alcance v1: solo fase/ready del Pod (via SandboxOrchestrator,
 * ya existente) mas quien lo ocupa -- SIN uso de CPU/memoria en vivo, que exigiria una
 * dependencia nueva (metrics-server) no confirmada en el cluster, ver DECISIONS.md.
 *
 * Deduplica por podName(): un Pod "cli" compartido tiene varias filas Sandbox (una por
 * asiento) pero es UN SOLO Pod real -- se le pregunta la fase a Kubernetes una sola vez por
 * Pod, no una vez por asiento.
 */
public class ListSandboxStatusUseCase {
    private final SandboxRepository sandboxRepository;
    private final SandboxOrchestrator sandboxOrchestrator;
    private final UserRepository userRepository;

    public ListSandboxStatusUseCase(final SandboxRepository sandboxRepository,
                                     final SandboxOrchestrator sandboxOrchestrator) {
        this(sandboxRepository, sandboxOrchestrator, null);
    }

    public ListSandboxStatusUseCase(final SandboxRepository sandboxRepository,
                                     final SandboxOrchestrator sandboxOrchestrator,
                                     final UserRepository userRepository) {
        this.sandboxRepository = sandboxRepository;
        this.sandboxOrchestrator = sandboxOrchestrator;
        this.userRepository = userRepository;
    }

    public record Seat(int seatIndex, String userUuid, String userDisplayName, Instant assignedAt) {
    }

    public record PodStatus(String sandboxUuid, String podName, String variant, String phase,
                            boolean ready, String reason, int restartCount, List<Seat> seats) {
    }

    public List<PodStatus> execute(final String conferenceUuid) {
        final List<Sandbox> sandboxes = sandboxRepository.findByConferenceUuid(conferenceUuid);

        // LinkedHashMap: conserva el orden de aparicion (mismo orden que ya devuelve el
        // repositorio, ORDER BY sandbox_slot) -- resultado estable para la UI.
        final Map<String, List<Sandbox>> byPod = new LinkedHashMap<>();
        for (final Sandbox s : sandboxes) {
            byPod.computeIfAbsent(s.podName(), k -> new ArrayList<>()).add(s);
        }

        final List<PodStatus> result = new ArrayList<>();
        for (final Map.Entry<String, List<Sandbox>> entry : byPod.entrySet()) {
            final String podName = entry.getKey();
            final List<Sandbox> seatsInPod = entry.getValue();
            final String variant = seatsInPod.get(0).getVariant();

            SandboxOrchestrator.RuntimeStatus runtimeStatus = sandboxOrchestrator.getRuntimeStatus(podName);
            // Mockito y adaptadores anteriores pueden no invocar el método default del puerto.
            if (runtimeStatus == null) {
                final String legacyPhase = sandboxOrchestrator.getPhase(podName);
                runtimeStatus = new SandboxOrchestrator.RuntimeStatus(legacyPhase,
                        legacyPhase != null && sandboxOrchestrator.isReady(podName), null, 0);
            }
            final String phase = runtimeStatus.phase();

            final List<Seat> seats = new ArrayList<>();
            for (final Sandbox s : seatsInPod) {
                seats.add(new Seat(s.getSeatIndex(), s.getUserUuid(), resolveDisplayName(s.getUserUuid()), s.getAssignedAt()));
            }
            // "NotFound" (no confundir con el "Unknown" que puede devolver getPhase para un Pod
            // que si existe pero sin status.phase todavia) -- el Pod no existe en Kubernetes (ej.
            // evicted/borrado a mano), la fila en SQLite sobrevive hasta la proxima purga/reintento.
            result.add(new PodStatus(seatsInPod.get(0).getUuid(), podName, variant,
                phase != null ? phase : "NotFound", runtimeStatus.ready(), runtimeStatus.reason(),
                runtimeStatus.restartCount(), seats));
        }
        return result;
    }

    private String resolveDisplayName(final String userUuid) {
        if (userUuid == null || userUuid.isBlank() || userRepository == null) return null;
        return userRepository.findByUuid(userUuid)
                .map(user -> {
                    if (user.getDisplayName() != null && !user.getDisplayName().isBlank()) return user.getDisplayName();
                    final String first = user.getFirstName() == null ? "" : user.getFirstName().trim();
                    final String last = user.getLastName() == null ? "" : user.getLastName().trim();
                    final String fullName = (first + " " + last).trim();
                    if (!fullName.isBlank()) return fullName;
                    if (user.getUsername() != null && !user.getUsername().isBlank()) return user.getUsername();
                    return "Usuario";
                })
                .orElse("Usuario");
    }
}
