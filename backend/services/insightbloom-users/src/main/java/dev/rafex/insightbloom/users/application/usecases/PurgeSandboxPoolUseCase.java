package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.Sandbox;
import dev.rafex.insightbloom.users.domain.ports.SandboxOrchestrator;
import dev.rafex.insightbloom.users.domain.ports.SandboxRepository;

import java.time.Instant;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PurgeSandboxPoolUseCase {
    private static final Logger LOGGER = Logger.getLogger(PurgeSandboxPoolUseCase.class.getName());

    private final SandboxRepository sandboxRepository;
    private final SandboxOrchestrator sandboxOrchestrator;

    public PurgeSandboxPoolUseCase(final SandboxRepository sandboxRepository,
                                    final SandboxOrchestrator sandboxOrchestrator) {
        this.sandboxRepository = sandboxRepository;
        this.sandboxOrchestrator = sandboxOrchestrator;
    }

    /**
     * Purga sandboxes cuya expiración ha pasado: borra el Pod+Service real primero (best-effort
     * por sandbox — si uno falla no bloquea el resto) y solo entonces limpia la fila en SQLite,
     * para no perder el registro de un Pod huérfano si el borrado en Kubernetes falla.
     * Ejecutado cada 5 minutos por el scheduler del bootstrap.
     *
     * @param now timestamp actual
     * @return cantidad de sandboxes eliminados
     */
    public int execute(final Instant now) {
        final List<Sandbox> expired = sandboxRepository.findExpired(now);
        for (final Sandbox sandbox : expired) {
            try {
                sandboxOrchestrator.deleteSandbox(sandbox.podName());
            } catch (final Exception e) {
                LOGGER.log(Level.WARNING, () -> "purge-sandbox: fallo al borrar pod "
                        + sandbox.podName() + ": " + e.getMessage());
            }
        }
        return sandboxRepository.deleteExpired(now);
    }
}
