package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.Conference;
import dev.rafex.insightbloom.users.domain.model.Sandbox;
import dev.rafex.insightbloom.users.domain.ports.ConferenceRepository;
import dev.rafex.insightbloom.users.domain.ports.SandboxOrchestrator;
import dev.rafex.insightbloom.users.domain.ports.SandboxRepository;

import java.time.Instant;
import java.util.List;

/**
 * Garantiza que una conferencia con CODE_IDE siempre tenga al menos un sandbox — asignado o
 * libre esperando (sin usuario) — en vez de arrancar en cero y crear el primer Pod recien cuando
 * un asistente lo pide (Pending->Running en caliente, con el asistente esperando el polling de
 * {@code IdePage.vue}). Se llama desde {@code ConferenceHandler#handleSetSandboxConfig}, es decir
 * cada vez que el organizador guarda/activa la configuracion de sandbox de su evento.
 *
 * Idempotente: si ya hay al menos un sandbox activo (asignado o libre) no hace nada. Best-effort
 * — un fallo aca (ej. Kubernetes no configurado en un entorno local) no debe romper el guardado
 * de la configuracion del organizador; el camino de creacion bajo demanda en
 * {@link AssignSandboxUseCase} sigue funcionando igual sin este pre-warm.
 */
public class EnsureUnassignedSandboxUseCase {
    private static final String DEFAULT_VARIANT = "python";
    private static final long DEFAULT_TTL_SECONDS = 4 * 3600; // sin fecha de evento: 4h desde ahora

    private final SandboxRepository sandboxRepository;
    private final ConferenceRepository conferenceRepository;
    private final SandboxOrchestrator sandboxOrchestrator;
    private final long ttlSecondsAfterEventExpiry;

    public EnsureUnassignedSandboxUseCase(final SandboxRepository sandboxRepository,
                                           final ConferenceRepository conferenceRepository,
                                           final SandboxOrchestrator sandboxOrchestrator,
                                           final long ttlSecondsAfterEventExpiry) {
        this.sandboxRepository = sandboxRepository;
        this.conferenceRepository = conferenceRepository;
        this.sandboxOrchestrator = sandboxOrchestrator;
        this.ttlSecondsAfterEventExpiry = ttlSecondsAfterEventExpiry;
    }

    public void execute(final String conferenceUuid) {
        final Conference conference = conferenceRepository.findByUuid(conferenceUuid)
            .orElseThrow(() -> new IllegalArgumentException("conference_not_found"));

        final List<Sandbox> active = sandboxRepository.findByConferenceUuid(conferenceUuid);
        if (!active.isEmpty()) {
            return;
        }

        final String variant = conference.getSandboxVariant() != null ? conference.getSandboxVariant() : DEFAULT_VARIANT;
        final boolean internetEnabled = conference.getSandboxInternetEnabled() != null
            && conference.getSandboxInternetEnabled() == 1;
        final Instant expiresAt = conference.getExpiresAt() != null
            ? conference.getExpiresAt().plusSeconds(ttlSecondsAfterEventExpiry)
            : Instant.now().plusSeconds(DEFAULT_TTL_SECONDS);

        final Sandbox sandbox = new Sandbox(conferenceUuid, 0, null, expiresAt);

        try {
            sandboxOrchestrator.createSandbox(sandbox.podName(), conferenceUuid, variant,
                conference.getSandboxExtraPackages(), conference.getSandboxRemoteGitUrl(), internetEnabled,
                conference.getSandboxJvmHeapMb(), conference.getSandboxSeatsPerPod());
        } catch (final IllegalStateException e) {
            if ("kubernetes_not_configured".equals(e.getMessage())) {
                return;
            }
            throw e;
        }

        try {
            sandboxRepository.save(sandbox);
        } catch (final RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("UNIQUE")) {
                // Otro request ya pre-provisiono el slot 0 en paralelo -- libera el Pod duplicado.
                sandboxOrchestrator.deleteSandbox(sandbox.podName());
                return;
            }
            throw e;
        }
    }
}
