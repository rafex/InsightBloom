package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.ports.SandboxRepository;
import java.time.Instant;

public class PurgeSandboxPoolUseCase {
    private final SandboxRepository sandboxRepository;

    public PurgeSandboxPoolUseCase(final SandboxRepository sandboxRepository) {
        this.sandboxRepository = sandboxRepository;
    }

    /**
     * Purga sandboxes cuya expiración ha pasado.
     * Ejecutado cada 5 minutos por el scheduler del bootstrap.
     *
     * @param now timestamp actual
     * @return cantidad de sandboxes eliminados
     */
    public int execute(final Instant now) {
        return sandboxRepository.deleteExpired(now);
    }
}
