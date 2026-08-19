package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.Sandbox;
import dev.rafex.insightbloom.users.domain.ports.SandboxOrchestrator;
import dev.rafex.insightbloom.users.domain.ports.SandboxRepository;
import dev.rafex.insightbloom.users.domain.ports.WorkspacePreviewPublisher;

import java.time.Instant;

public final class PublishWorkspacePreviewUseCase {
    /** Ver PublishAppPreviewUseCase.MAX_TTL_SECONDS -- mismo criterio, tope duro de 1h. */
    private static final long MAX_TTL_SECONDS = 3600;

    private final SandboxRepository sandboxRepository;
    private final SandboxOrchestrator sandboxOrchestrator;
    private final WorkspacePreviewPublisher publisher;

    public PublishWorkspacePreviewUseCase(final SandboxRepository sandboxRepository,
                                          final SandboxOrchestrator sandboxOrchestrator,
                                          final WorkspacePreviewPublisher publisher) {
        this.sandboxRepository = sandboxRepository;
        this.sandboxOrchestrator = sandboxOrchestrator;
        this.publisher = publisher;
    }

    public WorkspacePreviewPublisher.PreviewPublication execute(final String conferenceUuid,
                                                                final String userUuid,
                                                                final long ttlSeconds) {
        final Sandbox sandbox = requireActiveSandbox(conferenceUuid, userUuid);
        // En CLI multi-asiento el proceso de sandbox-agent mantiene la sesión de los asientos
        // en memoria. Después de un reinicio del Pod la fila de SQLite sigue asignada, pero el
        // agente todavía no conoce el asiento y devolvía workspace_not_found. Reaprovisiónalo
        // idempotentemente antes de leer el workspace; en IDE Web el adaptador no tiene seat
        // agent y este intento best-effort no cambia el flujo existente.
        if (Sandbox.isCliVariant(sandbox.getVariant())) {
            sandboxOrchestrator.ensureSeatReady(sandbox.podName(), sandbox.getSeatIndex(), userUuid);
        }
        final byte[] zip = sandboxOrchestrator.downloadWorkspaceZip(sandbox.podName(), sandbox.getSeatIndex());
        if (zip.length == 0) throw new IllegalArgumentException("workspace_empty");
        return publish(conferenceUuid, userUuid, zip, ttlSeconds);
    }

    /**
     * Publica el snapshot que el CLI ya auditó y empaquetó desde --root. El endpoint Web mantiene
     * el overload anterior, que descarga el workspace completo cuando recibe JSON vacío.
     */
    public WorkspacePreviewPublisher.PreviewPublication execute(final String conferenceUuid,
                                                                final String userUuid,
                                                                final byte[] workspaceZip,
                                                                final long ttlSeconds) {
        requireActiveSandbox(conferenceUuid, userUuid);
        if (workspaceZip == null || workspaceZip.length == 0) {
            throw new IllegalArgumentException("workspace_empty");
        }
        return publish(conferenceUuid, userUuid, workspaceZip, ttlSeconds);
    }

    private Sandbox requireActiveSandbox(final String conferenceUuid, final String userUuid) {
        final Sandbox sandbox = sandboxRepository.findByConferenceAndUser(conferenceUuid, userUuid)
                .orElseThrow(() -> new IllegalArgumentException("sandbox_not_assigned"));
        if (sandbox.getExpiresAt() != null && !sandbox.getExpiresAt().isAfter(Instant.now())) {
            throw new IllegalArgumentException("sandbox_expired");
        }
        return sandbox;
    }

    private WorkspacePreviewPublisher.PreviewPublication publish(final String conferenceUuid,
                                                                  final String userUuid,
                                                                  final byte[] workspaceZip,
                                                                  final long ttlSeconds) {
        return publisher.publish(conferenceUuid, userUuid, workspaceZip,
                Math.min(ttlSeconds, MAX_TTL_SECONDS));
    }
}
