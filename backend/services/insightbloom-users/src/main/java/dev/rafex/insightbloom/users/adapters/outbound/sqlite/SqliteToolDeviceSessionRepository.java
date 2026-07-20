package dev.rafex.insightbloom.users.adapters.outbound.sqlite;

import dev.rafex.insightbloom.users.domain.model.ToolDeviceSession;
import dev.rafex.insightbloom.users.domain.model.ToolKind;
import dev.rafex.insightbloom.users.domain.ports.ToolDeviceSessionRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SqliteToolDeviceSessionRepository implements ToolDeviceSessionRepository {
    private final DatabaseManager db;

    public SqliteToolDeviceSessionRepository(final DatabaseManager db) {
        this.db = db;
    }

    @Override
    public void save(final ToolDeviceSession session) {
        final String sql = """
            INSERT INTO tool_device_sessions
                (uuid, conference_uuid, user_uuid, tool, device_fingerprint, first_seen_at, last_seen_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """;
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, session.getUuid());
            ps.setString(2, session.getConferenceUuid());
            ps.setString(3, session.getUserUuid());
            ps.setString(4, session.getTool().name());
            ps.setString(5, session.getDeviceFingerprint());
            ps.setString(6, session.getFirstSeenAt().toString());
            ps.setString(7, session.getLastSeenAt().toString());
            ps.executeUpdate();
        } catch (final SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void touch(final String uuid) {
        final String sql = "UPDATE tool_device_sessions SET last_seen_at = ? WHERE uuid = ?";
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, Instant.now().toString());
            ps.setString(2, uuid);
            ps.executeUpdate();
        } catch (final SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Optional<ToolDeviceSession> findActive(final String conferenceUuid, final String userUuid,
                                                    final ToolKind tool, final String deviceFingerprint) {
        final String sql = """
            SELECT * FROM tool_device_sessions
            WHERE conference_uuid = ? AND user_uuid = ? AND tool = ? AND device_fingerprint = ?
                AND revoked_at IS NULL
        """;
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, conferenceUuid);
            ps.setString(2, userUuid);
            ps.setString(3, tool.name());
            ps.setString(4, deviceFingerprint);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (final SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<ToolDeviceSession> findActiveByUserAndTool(final String conferenceUuid, final String userUuid,
                                                             final ToolKind tool) {
        final String sql = """
            SELECT * FROM tool_device_sessions
            WHERE conference_uuid = ? AND user_uuid = ? AND tool = ? AND revoked_at IS NULL
            ORDER BY first_seen_at ASC
        """;
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, conferenceUuid);
            ps.setString(2, userUuid);
            ps.setString(3, tool.name());
            return queryList(ps);
        } catch (final SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<ToolDeviceSession> findActiveByDevice(final String conferenceUuid, final String deviceFingerprint) {
        final String sql = """
            SELECT * FROM tool_device_sessions
            WHERE conference_uuid = ? AND device_fingerprint = ? AND revoked_at IS NULL
            ORDER BY first_seen_at ASC
        """;
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, conferenceUuid);
            ps.setString(2, deviceFingerprint);
            return queryList(ps);
        } catch (final SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void revoke(final String uuid) {
        final String sql = "UPDATE tool_device_sessions SET revoked_at = ? WHERE uuid = ? AND revoked_at IS NULL";
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, Instant.now().toString());
            ps.setString(2, uuid);
            ps.executeUpdate();
        } catch (final SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void revokeAllForDevice(final String conferenceUuid, final String deviceFingerprint) {
        final String sql = """
            UPDATE tool_device_sessions SET revoked_at = ?
            WHERE conference_uuid = ? AND device_fingerprint = ? AND revoked_at IS NULL
        """;
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, Instant.now().toString());
            ps.setString(2, conferenceUuid);
            ps.setString(3, deviceFingerprint);
            ps.executeUpdate();
        } catch (final SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private static List<ToolDeviceSession> queryList(final PreparedStatement ps) throws SQLException {
        final List<ToolDeviceSession> result = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) result.add(map(rs));
        }
        return result;
    }

    private static ToolDeviceSession map(final ResultSet rs) throws SQLException {
        final String revokedStr = rs.getString("revoked_at");
        return new ToolDeviceSession(
            rs.getString("uuid"), rs.getString("conference_uuid"), rs.getString("user_uuid"),
            ToolKind.valueOf(rs.getString("tool")), rs.getString("device_fingerprint"),
            parseInstant(rs.getString("first_seen_at")), parseInstant(rs.getString("last_seen_at")),
            revokedStr != null ? parseInstant(revokedStr) : null
        );
    }

    private static Instant parseInstant(final String s) {
        if (s == null) return Instant.now();
        final String iso = s.contains("T") ? s : s.replace(" ", "T") + "Z";
        return Instant.parse(iso);
    }
}
