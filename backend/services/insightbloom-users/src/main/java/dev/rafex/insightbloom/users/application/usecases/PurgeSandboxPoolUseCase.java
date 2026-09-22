package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.Sandbox;
import dev.rafex.insightbloom.users.domain.ports.SandboxOrchestrator;
import dev.rafex.insightbloom.users.domain.ports.SandboxRepository;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
     * Purga sandboxes cuya expiración ha pasado: borra el Pod+Service real primero y solo después
     * limpia sus filas en SQLite. Un fallo de Kubernetes conserva las filas de ese Pod para que
     * el siguiente ciclo pueda reintentar, sin bloquear otros Pods vencidos.
     * Ejecutado cada 5 minutos por el scheduler del bootstrap.
     *
     * @param now timestamp actual
     * @return cantidad de sandboxes eliminados
     */
    public int execute(final Instant now) {
        final Map<PodKey, List<Sandbox>> expiredByPod = new LinkedHashMap<>();
        for (final Sandbox sandbox : sandboxRepository.findExpired(now)) {
            expiredByPod.computeIfAbsent(new PodKey(sandbox), ignored -> new java.util.ArrayList<>()).add(sandbox);
        }
        int deleted = 0;
        for (final Map.Entry<PodKey, List<Sandbox>> entry : expiredByPod.entrySet()) {
            final Sandbox sandbox = entry.getValue().getFirst();
            // Diagnostico (incidente 2026-07-19: sandboxes Web desaparecian sin explicacion clara
            // en los logs existentes) -- deja constancia de CADA sandbox purgado con su
            // expiresAt real antes de borrarlo, para poder confirmar si es un vencimiento
            // legitimo o un calculo de expiresAt corrompido en algun caso.
            LOGGER.info(() -> "purge-sandbox: purgando " + sandbox.podName()
                    + " (conference=" + sandbox.getConferenceUuid() + ", variant=" + sandbox.getVariant()
                    + ", user=" + sandbox.getUserUuid() + ", expiresAt=" + sandbox.getExpiresAt()
                    + ", createdAt=" + sandbox.getCreatedAt() + ", now=" + now + ")");
            try {
                sandboxOrchestrator.deleteSandbox(sandbox.podName());
                sandboxRepository.deletePod(sandbox.getConferenceUuid(), sandbox.getVariant(), sandbox.getSandboxSlot());
                deleted += entry.getValue().size();
            } catch (final Exception e) {
                LOGGER.log(Level.WARNING, () -> "purge-sandbox: fallo al borrar pod "
                        + sandbox.podName() + ": " + e.getMessage());
            }
        }
        return deleted;
    }

    private record PodKey(String conferenceUuid, String variant, int sandboxSlot) {
        private PodKey(final Sandbox sandbox) {
            this(sandbox.getConferenceUuid(), sandbox.getVariant(), sandbox.getSandboxSlot());
        }
    }
}
