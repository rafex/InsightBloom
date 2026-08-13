package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.ports.AuditLogQueryPort;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Cleanup policy: elimina logs de auditoría más antiguos que 1 año.
 * Ejecutable periódicamente por el scheduler (ej. 1 vez por mes).
 */
public class CleanupExpiredAuditLogsUseCase {
    private static final long RETENTION_DAYS = 365;
    private final AuditLogQueryPort auditLogQueryPort;

    public CleanupExpiredAuditLogsUseCase(final AuditLogQueryPort auditLogQueryPort) {
        this.auditLogQueryPort = auditLogQueryPort;
    }

    public int execute(final Instant now) {
        final String cutoffTime = now.minus(RETENTION_DAYS, ChronoUnit.DAYS).toString();
        return auditLogQueryPort.deleteOlderThan(cutoffTime);
    }
}
