package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.Conference;
import dev.rafex.insightbloom.users.domain.model.Sandbox;
import dev.rafex.insightbloom.users.domain.ports.ConferenceRepository;
import dev.rafex.insightbloom.users.domain.ports.SandboxOrchestrator;
import dev.rafex.insightbloom.users.domain.ports.SandboxRepository;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Garantiza que una conferencia con CODE_IDE siempre tenga al menos un sandbox — asignado o
 * libre esperando (sin usuario) — POR CADA VARIANTE (web/cli, ver Sandbox.VARIANT_WEB/
 * VARIANT_CLI), en vez de arrancar en cero y crear el primer Pod recien cuando un asistente lo
 * pide (Pending->Running en caliente, con el asistente esperando el polling de
 * {@code IdePage.vue}). Se llama desde {@code ConferenceHandler#handleSetSandboxConfig}, es decir
 * cada vez que el organizador guarda/activa la configuracion de sandbox de su evento.
 *
 * Idempotente por variante: si esa variante ya tiene al menos un sandbox activo (asignado o
 * libre) no hace nada para ella. Best-effort — un fallo aca (ej. Kubernetes no configurado en un
 * entorno local) no debe romper el guardado de la configuracion del organizador; el camino de
 * creacion bajo demanda en {@link AssignSandboxUseCase} sigue funcionando igual sin este pre-warm.
 */
public class EnsureUnassignedSandboxUseCase {
    private static final String IDE_MODE_TERMINAL_NVIM = "terminal-nvim";
    private static final String ORCHESTRATOR_VARIANT_WEB = "python";
    private static final int DEFAULT_POOL_SIZE = 1;
    private static final int DEFAULT_SEATS_PER_POD = 4;
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
        ensurePool(conferenceUuid, Sandbox.VARIANT_WEB, 1);
        ensurePool(conferenceUuid, Sandbox.VARIANT_CLI, 1);
    }

    /**
     * Pre-provisiona los slots faltantes hasta {@code desiredSlots}. Es idempotente y devuelve
     * cuántos Pods nuevos pudo crear; los errores de Kubernetes se manejan como best-effort para
     * que guardar la configuración del evento no dependa de que el cluster esté disponible.
     */
    public int ensurePool(final String conferenceUuid, final String variant, final int desiredSlots) {
        final Conference conference = conferenceRepository.findByUuid(conferenceUuid)
            .orElseThrow(() -> new IllegalArgumentException("conference_not_found"));
        final int target = Math.max(1, desiredSlots);
        final List<Sandbox> active = sandboxRepository.findByConferenceUuid(conferenceUuid);
        final Set<Integer> existingSlots = new HashSet<>();
        for (final Sandbox sandbox : active) {
            if (variant.equals(sandbox.getVariant())) {
                existingSlots.add(sandbox.getSandboxSlot());
            }
        }

        int created = 0;
        for (int slot = 0; slot < target; slot++) {
            if (existingSlots.contains(slot)) continue;
            if (preWarm(conference, conferenceUuid, variant, slot,
                    Sandbox.VARIANT_CLI.equals(variant) ? IDE_MODE_TERMINAL_NVIM : ORCHESTRATOR_VARIANT_WEB)) {
                existingSlots.add(slot);
                created++;
            }
        }
        return created;
    }

    /**
     * Reposición ligera después de una asignación. Solo crea el siguiente Pod si no queda un
     * asiento libre en los Pods existentes y aún existe capacidad configurada. En Web esto
     * conserva un Pod libre; en CLI reutiliza primero los asientos libres del Pod compartido.
     */
    public void ensureSpare(final String conferenceUuid, final String variant) {
        final Conference conference = conferenceRepository.findByUuid(conferenceUuid)
            .orElseThrow(() -> new IllegalArgumentException("conference_not_found"));
        final int poolSize = poolSizeFor(variant, conference);
        final int seatsPerPod = seatsPerPodFor(variant, conference);
        final List<Sandbox> active = sandboxRepository.findByConferenceUuid(conferenceUuid).stream()
            .filter(s -> variant.equals(s.getVariant()))
            .toList();

        if (active.stream().anyMatch(s -> s.getUserUuid() == null)) return;

        final Map<Integer, Long> occupiedBySlot = active.stream()
            .collect(java.util.stream.Collectors.groupingBy(Sandbox::getSandboxSlot,
                java.util.stream.Collectors.counting()));
        if (occupiedBySlot.values().stream().anyMatch(count -> count < seatsPerPod)) return;
        if (occupiedBySlot.size() >= poolSize) return;

        int nextSlot = 0;
        while (occupiedBySlot.containsKey(nextSlot) && nextSlot < poolSize) nextSlot++;
        if (nextSlot < poolSize) {
            preWarm(conference, conferenceUuid, variant, nextSlot,
                Sandbox.VARIANT_CLI.equals(variant) ? IDE_MODE_TERMINAL_NVIM : ORCHESTRATOR_VARIANT_WEB);
        }
    }

    private int poolSizeFor(final String variant, final Conference conference) {
        final Integer configured = Sandbox.VARIANT_CLI.equals(variant)
            ? conference.getSandboxCliPoolSize() : conference.getSandboxPoolSize();
        return configured != null ? configured : DEFAULT_POOL_SIZE;
    }

    private int seatsPerPodFor(final String variant, final Conference conference) {
        if (!Sandbox.VARIANT_CLI.equals(variant)) return 1;
        return conference.getSandboxSeatsPerPod() != null
            ? conference.getSandboxSeatsPerPod() : DEFAULT_SEATS_PER_POD;
    }

    private boolean preWarm(final Conference conference, final String conferenceUuid, final String variant,
                             final int sandboxSlot, final String orchestratorVariant) {
        final boolean internetEnabled = conference.getSandboxInternetEnabled() != null
            && conference.getSandboxInternetEnabled() == 1;
        final Instant expiresAt = conference.getExpiresAt() != null
            ? conference.getExpiresAt().plusSeconds(ttlSecondsAfterEventExpiry)
            : Instant.now().plusSeconds(DEFAULT_TTL_SECONDS);

        final Sandbox sandbox = new Sandbox(conferenceUuid, sandboxSlot, 0, variant, null, expiresAt);

        try {
            sandboxOrchestrator.createSandbox(sandbox.podName(), conferenceUuid, orchestratorVariant,
                conference.getSandboxRemoteGitUrl(), internetEnabled,
                conference.getSandboxJvmHeapMb(), conference.getSandboxSeatsPerPod());
        } catch (final IllegalStateException e) {
            if ("kubernetes_not_configured".equals(e.getMessage())) {
                return false;
            }
            throw e;
        }

        try {
            sandboxRepository.save(sandbox);
        } catch (final RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("UNIQUE")) {
                // Otro request ya pre-provisionó este slot de la variante en paralelo -- libera
                // el Pod duplicado.
                sandboxOrchestrator.deleteSandbox(sandbox.podName());
                return false;
            }
            throw e;
        }
        return true;
    }
}
