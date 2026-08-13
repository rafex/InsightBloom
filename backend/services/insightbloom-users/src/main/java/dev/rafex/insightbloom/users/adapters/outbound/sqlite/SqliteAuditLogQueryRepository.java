package dev.rafex.insightbloom.users.adapters.outbound.sqlite;

import dev.rafex.insightbloom.users.domain.ports.AuditLogQueryPort;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class SqliteAuditLogQueryRepository implements AuditLogQueryPort {
    private final DatabaseManager database;

    public SqliteAuditLogQueryRepository(final DatabaseManager database) {
        this.database = database;
    }

    @Override
    public List<AuditLogEntry> findByActor(final String actorUuid, final int limit, final int offset) {
        try (var c = database.getConnection(); var ps = c.prepareStatement("""
                SELECT uuid, timestamp, actor_uuid, action, resource_type, resource_id,
                       changes, ip_address, user_agent, status, error_message, additional_context
                FROM audit_logs WHERE actor_uuid = ?
                ORDER BY timestamp DESC LIMIT ? OFFSET ?""")) {
            ps.setString(1, actorUuid);
            ps.setInt(2, limit);
            ps.setInt(3, offset);
            try (var rs = ps.executeQuery()) {
                final List<AuditLogEntry> result = new ArrayList<>();
                while (rs.next()) result.add(read(rs));
                return result;
            }
        } catch (final SQLException e) {
            throw new RuntimeException("Failed to list audit logs by actor", e);
        }
    }

    @Override
    public List<AuditLogEntry> findAll(final int limit, final int offset) {
        try (var c = database.getConnection(); var ps = c.prepareStatement("""
                SELECT uuid, timestamp, actor_uuid, action, resource_type, resource_id,
                       changes, ip_address, user_agent, status, error_message, additional_context
                FROM audit_logs
                ORDER BY timestamp DESC LIMIT ? OFFSET ?""")) {
            ps.setInt(1, limit);
            ps.setInt(2, offset);
            try (var rs = ps.executeQuery()) {
                final List<AuditLogEntry> result = new ArrayList<>();
                while (rs.next()) result.add(read(rs));
                return result;
            }
        } catch (final SQLException e) {
            throw new RuntimeException("Failed to list all audit logs", e);
        }
    }

    @Override
    public List<AuditLogEntry> findByResource(final String resourceType, final String resourceId,
                                              final int limit, final int offset) {
        try (var c = database.getConnection(); var ps = c.prepareStatement("""
                SELECT uuid, timestamp, actor_uuid, action, resource_type, resource_id,
                       changes, ip_address, user_agent, status, error_message, additional_context
                FROM audit_logs WHERE resource_type = ? AND resource_id = ?
                ORDER BY timestamp DESC LIMIT ? OFFSET ?""")) {
            ps.setString(1, resourceType);
            ps.setString(2, resourceId);
            ps.setInt(3, limit);
            ps.setInt(4, offset);
            try (var rs = ps.executeQuery()) {
                final List<AuditLogEntry> result = new ArrayList<>();
                while (rs.next()) result.add(read(rs));
                return result;
            }
        } catch (final SQLException e) {
            throw new RuntimeException("Failed to list audit logs by resource", e);
        }
    }

    @Override
    public int deleteOlderThan(final String cutoffTime) {
        try (var c = database.getConnection(); var ps = c.prepareStatement(
                "DELETE FROM audit_logs WHERE timestamp < ?")) {
            ps.setString(1, cutoffTime);
            return ps.executeUpdate();
        } catch (final SQLException e) {
            throw new RuntimeException("Failed to delete old audit logs", e);
        }
    }

    private AuditLogEntry read(final java.sql.ResultSet rs) throws SQLException {
        return new AuditLogEntry(
            rs.getString("uuid"),
            rs.getString("timestamp"),
            rs.getString("actor_uuid"),
            rs.getString("action"),
            rs.getString("resource_type"),
            rs.getString("resource_id"),
            rs.getString("changes"),
            rs.getString("ip_address"),
            rs.getString("user_agent"),
            rs.getString("status"),
            rs.getString("error_message"),
            rs.getString("additional_context"));
    }
}
