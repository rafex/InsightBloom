package dev.rafex.insightbloom.users.adapters.outbound.sqlite;

import dev.rafex.insightbloom.users.domain.ports.AuditLogPort;

import java.sql.SQLException;

public class SqliteAuditLogRepository implements AuditLogPort {
    private final DatabaseManager database;

    public SqliteAuditLogRepository(final DatabaseManager database) {
        this.database = database;
    }

    @Override
    public void logAction(final String uuid, final String timestamp, final String actorUuid,
                         final String action, final String resourceType, final String resourceId,
                         final String changes, final String ipAddress, final String userAgent,
                         final String status, final String errorMessage, final String additionalContext) {
        try (var c = database.getConnection(); var ps = c.prepareStatement("""
                INSERT INTO audit_logs (uuid, timestamp, actor_uuid, action, resource_type, resource_id,
                    changes, ip_address, user_agent, status, error_message, additional_context)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""")) {
            ps.setString(1, uuid);
            ps.setString(2, timestamp);
            ps.setString(3, actorUuid);
            ps.setString(4, action);
            ps.setString(5, resourceType);
            ps.setString(6, resourceId);
            ps.setString(7, changes);
            ps.setString(8, ipAddress);
            ps.setString(9, userAgent);
            ps.setString(10, status);
            ps.setString(11, errorMessage);
            ps.setString(12, additionalContext);
            ps.executeUpdate();
        } catch (final SQLException e) {
            // Best-effort: log but don't fail the action if audit fails
            System.err.println("Failed to log audit: " + e.getMessage());
        }
    }
}
