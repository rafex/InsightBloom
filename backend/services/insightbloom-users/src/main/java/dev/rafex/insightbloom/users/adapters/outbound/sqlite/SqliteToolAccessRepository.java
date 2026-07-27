package dev.rafex.insightbloom.users.adapters.outbound.sqlite;

import dev.rafex.insightbloom.users.domain.model.ToolKey;
import dev.rafex.insightbloom.users.domain.ports.ToolAccessRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Mismo patrón que SqliteSurveyAccessRepository (insightbloom-survey), con tool_key extra. */
public class SqliteToolAccessRepository implements ToolAccessRepository {
    private static final String ALL_USERS = "*";
    private final DatabaseManager db;

    public SqliteToolAccessRepository(final DatabaseManager db) {
        this.db = db;
    }

    @Override
    public boolean isReleased(final String conferenceUuid, final ToolKey toolKey, final String userUuid) {
        final String sql = "SELECT 1 FROM tool_access_releases WHERE conference_uuid = ? AND tool_key = ? "
                + "AND (user_uuid = ? OR user_uuid = ?) LIMIT 1";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, conferenceUuid);
            ps.setString(2, toolKey.name());
            ps.setString(3, userUuid);
            ps.setString(4, ALL_USERS);
            try (var rs = ps.executeQuery()) { return rs.next(); }
        } catch (final SQLException e) {
            throw new RuntimeException("Failed to check tool access", e);
        }
    }

    @Override
    public boolean isReleasedForAll(final String conferenceUuid, final ToolKey toolKey) {
        final String sql = "SELECT 1 FROM tool_access_releases WHERE conference_uuid = ? AND tool_key = ? "
                + "AND user_uuid = ? LIMIT 1";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, conferenceUuid);
            ps.setString(2, toolKey.name());
            ps.setString(3, ALL_USERS);
            try (var rs = ps.executeQuery()) { return rs.next(); }
        } catch (final SQLException e) {
            throw new RuntimeException("Failed to check global tool access", e);
        }
    }

    @Override
    public Set<ToolKey> resolveReleasedForUser(final String conferenceUuid, final String userUuid) {
        final String sql = "SELECT DISTINCT tool_key FROM tool_access_releases WHERE conference_uuid = ? "
                + "AND (user_uuid = ? OR user_uuid = ?)";
        final Set<ToolKey> released = EnumSet.noneOf(ToolKey.class);
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, conferenceUuid);
            ps.setString(2, userUuid);
            ps.setString(3, ALL_USERS);
            try (var rs = ps.executeQuery()) {
                while (rs.next()) {
                    try {
                        released.add(ToolKey.valueOf(rs.getString("tool_key")));
                    } catch (final IllegalArgumentException ignored) { /* clave desconocida/obsoleta */ }
                }
            }
        } catch (final SQLException e) {
            throw new RuntimeException("Failed to resolve tool access", e);
        }
        return released;
    }

    @Override
    public List<String> releasedUserUuids(final String conferenceUuid, final ToolKey toolKey) {
        final String sql = "SELECT user_uuid FROM tool_access_releases WHERE conference_uuid = ? "
                + "AND tool_key = ? AND user_uuid != ?";
        final List<String> result = new ArrayList<>();
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, conferenceUuid);
            ps.setString(2, toolKey.name());
            ps.setString(3, ALL_USERS);
            try (var rs = ps.executeQuery()) {
                while (rs.next()) result.add(rs.getString("user_uuid"));
            }
        } catch (final SQLException e) {
            throw new RuntimeException("Failed to list released users", e);
        }
        return result;
    }

    @Override
    public void releaseForAll(final String conferenceUuid, final ToolKey toolKey) {
        releaseUsers(conferenceUuid, toolKey, List.of(ALL_USERS));
    }

    @Override
    public void releaseUsers(final String conferenceUuid, final ToolKey toolKey, final List<String> userUuids) {
        if (userUuids == null || userUuids.isEmpty()) return;
        final String sql = "INSERT OR IGNORE INTO tool_access_releases"
                + "(conference_uuid, tool_key, user_uuid, released_at) VALUES (?, ?, ?, ?)";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            for (final String userUuid : userUuids) {
                ps.setString(1, conferenceUuid);
                ps.setString(2, toolKey.name());
                ps.setString(3, userUuid);
                ps.setString(4, Instant.now().toString());
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (final SQLException e) {
            throw new RuntimeException("Failed to release tool access", e);
        }
    }

    @Override
    public void lockForAll(final String conferenceUuid, final ToolKey toolKey) {
        final String sql = "DELETE FROM tool_access_releases WHERE conference_uuid = ? AND tool_key = ?";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, conferenceUuid);
            ps.setString(2, toolKey.name());
            ps.executeUpdate();
        } catch (final SQLException e) {
            throw new RuntimeException("Failed to lock tool access", e);
        }
    }

    @Override
    public void lockUsers(final String conferenceUuid, final ToolKey toolKey, final List<String> userUuids) {
        if (userUuids == null || userUuids.isEmpty()) return;
        final String sql = "DELETE FROM tool_access_releases WHERE conference_uuid = ? AND tool_key = ? AND user_uuid = ?";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            for (final String userUuid : userUuids) {
                ps.setString(1, conferenceUuid);
                ps.setString(2, toolKey.name());
                ps.setString(3, userUuid);
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (final SQLException e) {
            throw new RuntimeException("Failed to lock tool access for users", e);
        }
    }

    @Override
    public void releaseAllTools(final String conferenceUuid, final List<ToolKey> toolKeys) {
        final String sql = "INSERT OR IGNORE INTO tool_access_releases"
                + "(conference_uuid, tool_key, user_uuid, released_at) VALUES (?, ?, ?, ?)";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            final String now = Instant.now().toString();
            for (final ToolKey toolKey : toolKeys) {
                ps.setString(1, conferenceUuid);
                ps.setString(2, toolKey.name());
                ps.setString(3, ALL_USERS);
                ps.setString(4, now);
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (final SQLException e) {
            throw new RuntimeException("Failed to release all tool access", e);
        }
    }
}
