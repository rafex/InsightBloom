package dev.rafex.insightbloom.users.domain.ports;

/**
 * Puerto para persistencia de auditoría. Registra acciones sensibles (DELETE eventos,
 * cambios de rol, etc.) para compliance y forensics.
 */
public interface AuditLogPort {
    void logAction(String uuid, String timestamp, String actorUuid, String action,
                   String resourceType, String resourceId, String changes,
                   String ipAddress, String userAgent, String status, String errorMessage,
                   String additionalContext);
}
