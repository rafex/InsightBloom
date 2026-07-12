package dev.rafex.insightbloom.users.adapters.outbound.sqlite;

import dev.rafex.insightbloom.users.domain.model.EventRole;
import dev.rafex.insightbloom.users.domain.ports.EventRoleRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SqliteEventRoleRepository implements EventRoleRepository {
    private final DatabaseManager db;

    public SqliteEventRoleRepository(final DatabaseManager db) {
        this.db = db;
    }

    @Override
    public void save(final EventRole eventRole) {
        final String sql = """
            INSERT OR REPLACE INTO event_roles (uuid, event_uuid, user_uuid, role_key, assigned_at)
            VALUES (?, ?, ?, ?, ?)
        """;
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, eventRole.getUuid());
            ps.setString(2, eventRole.getEventUuid());
            ps.setString(3, eventRole.getUserUuid());
            ps.setString(4, eventRole.getRoleKey());
            ps.setString(5, eventRole.getAssignedAt().toString());
            ps.executeUpdate();
        } catch (final SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void delete(final String eventUuid, final String userUuid) {
        final String sql = "DELETE FROM event_roles WHERE event_uuid = ? AND user_uuid = ?";
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, eventUuid);
            ps.setString(2, userUuid);
            ps.executeUpdate();
        } catch (final SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Optional<EventRole> findByEventAndUser(final String eventUuid, final String userUuid) {
        final String sql = "SELECT * FROM event_roles WHERE event_uuid = ? AND user_uuid = ?";
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, eventUuid);
            ps.setString(2, userUuid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(map(rs));
            }
        } catch (final SQLException e) {
            throw new RuntimeException(e);
        }
        return Optional.empty();
    }

    @Override
    public List<EventRole> findByEvent(final String eventUuid) {
        final List<EventRole> list = new ArrayList<>();
        final String sql = "SELECT * FROM event_roles WHERE event_uuid = ? ORDER BY assigned_at";
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, eventUuid);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(map(rs));
            }
        } catch (final SQLException e) {
            throw new RuntimeException(e);
        }
        return list;
    }

    private static EventRole map(final ResultSet rs) throws SQLException {
        return new EventRole(rs.getString("uuid"), rs.getString("event_uuid"), rs.getString("user_uuid"),
                rs.getString("role_key"), Instant.parse(rs.getString("assigned_at")));
    }
}
