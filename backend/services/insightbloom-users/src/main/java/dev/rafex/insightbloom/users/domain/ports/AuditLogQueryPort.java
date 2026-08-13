package dev.rafex.insightbloom.users.domain.ports;

import java.util.List;

/**
 * Puerto de lectura para auditoría. Permite consultar logs (usuario sus propios, admin todos).
 */
public interface AuditLogQueryPort {
    /**
     * Obtiene logs donde el usuario es el actor (acciones que el usuario realizó).
     */
    List<AuditLogEntry> findByActor(String actorUuid, int limit, int offset);

    /**
     * Obtiene todos los logs (solo para admin).
     */
    List<AuditLogEntry> findAll(int limit, int offset);

    /**
     * Obtiene logs para un recurso específico.
     */
    List<AuditLogEntry> findByResource(String resourceType, String resourceId, int limit, int offset);

    /**
     * Borra logs con timestamp anterior a cutoffTime (policy de retención).
     */
    int deleteOlderThan(String cutoffTime);

    record AuditLogEntry(String uuid, String timestamp, String actorUuid, String action,
                        String resourceType, String resourceId, String changes,
                        String ipAddress, String userAgent, String status, String errorMessage,
                        String additionalContext) {}
}
