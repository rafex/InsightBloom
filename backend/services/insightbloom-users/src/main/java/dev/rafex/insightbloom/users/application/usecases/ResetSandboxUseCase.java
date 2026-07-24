package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.Conference;
import dev.rafex.insightbloom.users.domain.model.Sandbox;
import dev.rafex.insightbloom.users.domain.ports.ConferenceRepository;
import dev.rafex.insightbloom.users.domain.ports.SandboxOrchestrator;
import dev.rafex.insightbloom.users.domain.ports.SandboxRepository;

import java.util.List;

/**
 * Restablece o elimina un Pod para que recoja la imagen y la configuración actuales.
 *
 * <p>El identificador recibido por la API es el UUID de una fila Sandbox, pero la unidad real
 * que se elimina es el Pod completo (slot + variante). Esto evita dejar asientos de un Pod CLI
 * compartido apuntando a un recurso que ya no existe. La recreación exige que el Pod esté libre;
 * la eliminación también puede forzarse sobre un Pod ocupado, porque esa operación está pensada
 * para liberar un recurso roto o aplicar una limpieza administrativa explícita.</p>
 */
public class ResetSandboxUseCase {
    private static final int DEFAULT_POOL_SIZE = 1;

    private final SandboxRepository sandboxRepository;
    private final ConferenceRepository conferenceRepository;
    private final SandboxOrchestrator sandboxOrchestrator;
    private final EnsureUnassignedSandboxUseCase ensureUnassignedSandboxUseCase;

    public ResetSandboxUseCase(final SandboxRepository sandboxRepository,
                               final ConferenceRepository conferenceRepository,
                               final SandboxOrchestrator sandboxOrchestrator,
                               final EnsureUnassignedSandboxUseCase ensureUnassignedSandboxUseCase) {
        this.sandboxRepository = sandboxRepository;
        this.conferenceRepository = conferenceRepository;
        this.sandboxOrchestrator = sandboxOrchestrator;
        this.ensureUnassignedSandboxUseCase = ensureUnassignedSandboxUseCase;
    }

    public record Result(String conferenceUuid, String sandboxUuid, String podName,
                         String variant, int sandboxSlot, String action, int recreatedPods) {
    }

    public Result delete(final String conferenceUuid, final String sandboxUuid) {
        return reset(conferenceUuid, sandboxUuid, false);
    }

    public Result recreate(final String conferenceUuid, final String sandboxUuid) {
        return reset(conferenceUuid, sandboxUuid, true);
    }

    private Result reset(final String conferenceUuid, final String sandboxUuid, final boolean recreate) {
        // El borrado no necesita reconstruir ni guardar la conferencia: la autorización ya fue
        // validada por el handler y el UUID del sandbox vuelve a comprobarse abajo. Evitar cargar
        // un agregado Conference en esta rama mantiene la operación aislada de los campos NOT
        // NULL de conferences (en particular name) y evita que una limpieza de Pods dependa de
        // una escritura accidental de la conferencia.
        final Conference conference = recreate
            ? conferenceRepository.findByUuid(conferenceUuid)
                .orElseThrow(() -> new IllegalArgumentException("conference_not_found"))
            : null;
        final Sandbox requested = sandboxRepository.findByUuid(sandboxUuid)
            .orElseThrow(() -> new IllegalArgumentException("sandbox_not_found"));
        if (!conferenceUuid.equals(requested.getConferenceUuid())) {
            throw new IllegalArgumentException("sandbox_not_in_conference");
        }

        final List<Sandbox> samePod = sandboxRepository.findByConferenceUuid(conferenceUuid).stream()
            .filter(s -> requested.getVariant().equals(s.getVariant()))
            .filter(s -> requested.getSandboxSlot() == s.getSandboxSlot())
            .toList();
        if (samePod.isEmpty()) {
            throw new IllegalArgumentException("sandbox_not_found");
        }
        // Recrear reemplaza inmediatamente el Pod y, por seguridad, nunca debe destruir el
        // workspace de un alumno. Eliminar es distinto: el moderador confirmó una operación
        // destructiva y debe poder desalojar también un Pod ocupado para recuperar capacidad.
        if (recreate && samePod.stream().anyMatch(s -> s.getUserUuid() != null)) {
            throw new IllegalArgumentException("sandbox_in_use");
        }

        // Borramos el recurso real antes de quitar sus filas: si Kubernetes falla, el estado
        // queda visible y el organizador puede reintentar, en lugar de crear un Pod huérfano.
        sandboxOrchestrator.deleteSandbox(requested.podName());
        sandboxRepository.deletePod(conferenceUuid, requested.getVariant(), requested.getSandboxSlot());

        int recreatedPods = 0;
        if (recreate) {
            final int desiredPool = requested.getVariant().equals(Sandbox.VARIANT_CLI)
                ? configuredPool(conference.getSandboxCliPoolSize())
                : configuredPool(conference.getSandboxPoolSize());
            recreatedPods = ensureUnassignedSandboxUseCase.ensurePool(
                conferenceUuid, requested.getVariant(), desiredPool);
        }
        return new Result(conferenceUuid, sandboxUuid, requested.podName(), requested.getVariant(),
            requested.getSandboxSlot(), recreate ? "recreated" : "deleted", recreatedPods);
    }

    private static int configuredPool(final Integer configured) {
        return configured != null ? configured : DEFAULT_POOL_SIZE;
    }
}
