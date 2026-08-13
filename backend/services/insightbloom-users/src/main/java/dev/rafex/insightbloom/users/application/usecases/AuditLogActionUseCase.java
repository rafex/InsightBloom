package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.ports.AuditLogPort;

import java.time.Instant;
import java.util.UUID;

/**
 * Best-effort auditoría de acciones sensibles: DELETE eventos, cambios de rol, acceso a datos.
 * Si el logging falla, la acción continúa -- el audit es "nice to have", no critical path.
 */
public class AuditLogActionUseCase {
    private final AuditLogPort auditLogPort;

    public AuditLogActionUseCase(final AuditLogPort auditLogPort) {
        this.auditLogPort = auditLogPort;
    }

    public void logAction(final String actorUuid, final String action, final String resourceType,
                         final String resourceId, final String ipAddress, final String userAgent) {
        logAction(actorUuid, action, resourceType, resourceId, null, ipAddress, userAgent,
                  "SUCCESS", null, null);
    }

    public void logAction(final String actorUuid, final String action, final String resourceType,
                         final String resourceId, final String changes, final String ipAddress,
                         final String userAgent, final String status, final String errorMessage,
                         final String additionalContext) {
        try {
            auditLogPort.logAction(
                UUID.randomUUID().toString(),
                Instant.now().toString(),
                actorUuid,
                action,
                resourceType,
                resourceId,
                changes,
                ipAddress,
                userAgent,
                status,
                errorMessage,
                additionalContext);
        } catch (final Exception ignored) {
            // Best-effort: if audit fails, don't propagate
        }
    }
}
