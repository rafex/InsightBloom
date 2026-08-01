package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.Sandbox;
import dev.rafex.insightbloom.users.domain.ports.SandboxOrchestrator;
import dev.rafex.insightbloom.users.domain.ports.SandboxRepository;
import dev.rafex.insightbloom.users.domain.ports.WorkspacePreviewPublisher;

import java.time.Instant;

public final class PublishWorkspacePreviewUseCase {
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
        final Sandbox sandbox = sandboxRepository.findByConferenceAndUser(conferenceUuid, userUuid)
                .orElseThrow(() -> new IllegalArgumentException("sandbox_not_assigned"));
        if (sandbox.getExpiresAt() != null && !sandbox.getExpiresAt().isAfter(Instant.now())) {
            throw new IllegalArgumentException("sandbox_expired");
        }
        // En CLI multi-asiento el proceso de sandbox-agent mantiene la sesión de los asientos
        // en memoria. Después de un reinicio del Pod la fila de SQLite sigue asignada, pero el
        // agente todavía no conoce el asiento y devolvía workspace_not_found. Reaprovisiónalo
        // idempotentemente antes de leer el workspace; en IDE Web el adaptador no tiene seat
        // agent y este intento best-effort no cambia el flujo existente.
        if (Sandbox.VARIANT_CLI.equals(sandbox.getVariant())) {
            sandboxOrchestrator.ensureSeatReady(sandbox.podName(), sandbox.getSeatIndex(), userUuid);
        }
        final byte[] zip = sandboxOrchestrator.downloadWorkspaceZip(sandbox.podName(), sandbox.getSeatIndex());
        if (zip.length == 0) throw new IllegalArgumentException("workspace_empty");
        return publisher.publish(conferenceUuid, userUuid, zip, ttlSeconds);
    }
}
