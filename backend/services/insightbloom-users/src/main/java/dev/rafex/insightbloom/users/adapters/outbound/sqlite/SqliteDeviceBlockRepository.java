package dev.rafex.insightbloom.users.adapters.outbound.sqlite;

import dev.rafex.insightbloom.users.domain.model.DeviceBlock;
import dev.rafex.insightbloom.users.domain.ports.DeviceBlockRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SqliteDeviceBlockRepository implements DeviceBlockRepository {
    private final DatabaseManager db;

    public SqliteDeviceBlockRepository(final DatabaseManager db) {
        this.db = db;
    }

    @Override
    public void save(final DeviceBlock block) {
        final String sql = """
            INSERT INTO device_blocks (uuid, conference_uuid, device_fingerprint, account_count, blocked_at)
            VALUES (?, ?, ?, ?, ?)
        """;
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, block.getUuid());
            ps.setString(2, block.getConferenceUuid());
            ps.setString(3, block.getDeviceFingerprint());
            ps.setInt(4, block.getAccountCount());
            ps.setString(5, block.getBlockedAt().toString());
            ps.executeUpdate();
        } catch (final SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Optional<DeviceBlock> findActive(final String conferenceUuid, final String deviceFingerprint) {
        final String sql = """
            SELECT * FROM device_blocks
            WHERE conference_uuid = ? AND device_fingerprint = ? AND unblocked_at IS NULL
        """;
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, conferenceUuid);
            ps.setString(2, deviceFingerprint);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (final SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<DeviceBlock> findByConference(final String conferenceUuid) {
        final String sql = "SELECT * FROM device_blocks WHERE conference_uuid = ? ORDER BY blocked_at DESC";
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, conferenceUuid);
            final List<DeviceBlock> result = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(map(rs));
            }
            return result;
        } catch (final SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void unblock(final String uuid, final String unblockedByUserUuid) {
        final String sql = """
            UPDATE device_blocks SET unblocked_at = ?, unblocked_by = ?
            WHERE uuid = ? AND unblocked_at IS NULL
        """;
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, Instant.now().toString());
            ps.setString(2, unblockedByUserUuid);
            ps.setString(3, uuid);
            ps.executeUpdate();
        } catch (final SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private static DeviceBlock map(final ResultSet rs) throws SQLException {
        final String unblockedStr = rs.getString("unblocked_at");
        return new DeviceBlock(
            rs.getString("uuid"), rs.getString("conference_uuid"), rs.getString("device_fingerprint"),
            rs.getInt("account_count"), parseInstant(rs.getString("blocked_at")),
            unblockedStr != null ? parseInstant(unblockedStr) : null, rs.getString("unblocked_by")
        );
    }

    private static Instant parseInstant(final String s) {
        if (s == null) return Instant.now();
        final String iso = s.contains("T") ? s : s.replace(" ", "T") + "Z";
        return Instant.parse(iso);
    }
}
