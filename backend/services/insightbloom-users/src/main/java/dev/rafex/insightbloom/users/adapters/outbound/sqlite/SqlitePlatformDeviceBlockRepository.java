package dev.rafex.insightbloom.users.adapters.outbound.sqlite;

import dev.rafex.insightbloom.users.domain.model.PlatformDeviceBlock;
import dev.rafex.insightbloom.users.domain.model.PlatformDeviceBlockReason;
import dev.rafex.insightbloom.users.domain.ports.PlatformDeviceBlockRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SqlitePlatformDeviceBlockRepository implements PlatformDeviceBlockRepository {
    private final DatabaseManager db;

    public SqlitePlatformDeviceBlockRepository(final DatabaseManager db) {
        this.db = db;
    }

    @Override
    public void save(final PlatformDeviceBlock block) {
        final String sql = """
            INSERT INTO platform_device_blocks (uuid, device_fingerprint, reason, related_count, blocked_at)
            VALUES (?, ?, ?, ?, ?)
        """;
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, block.getUuid());
            ps.setString(2, block.getDeviceFingerprint());
            ps.setString(3, block.getReason().name());
            ps.setInt(4, block.getRelatedCount());
            ps.setString(5, block.getBlockedAt().toString());
            ps.executeUpdate();
        } catch (final SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Optional<PlatformDeviceBlock> findActive(final String deviceFingerprint) {
        final String sql = """
            SELECT * FROM platform_device_blocks
            WHERE device_fingerprint = ? AND unblocked_at IS NULL
        """;
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, deviceFingerprint);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (final SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<PlatformDeviceBlock> findAll() {
        final String sql = "SELECT * FROM platform_device_blocks ORDER BY blocked_at DESC";
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            final List<PlatformDeviceBlock> result = new ArrayList<>();
            while (rs.next()) result.add(map(rs));
            return result;
        } catch (final SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void unblock(final String uuid, final String unblockedByUserUuid) {
        final String sql = """
            UPDATE platform_device_blocks SET unblocked_at = ?, unblocked_by = ?
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

    private static PlatformDeviceBlock map(final ResultSet rs) throws SQLException {
        final String unblockedStr = rs.getString("unblocked_at");
        return new PlatformDeviceBlock(
            rs.getString("uuid"), rs.getString("device_fingerprint"),
            PlatformDeviceBlockReason.valueOf(rs.getString("reason")), rs.getInt("related_count"),
            parseInstant(rs.getString("blocked_at")),
            unblockedStr != null ? parseInstant(unblockedStr) : null, rs.getString("unblocked_by")
        );
    }

    private static Instant parseInstant(final String s) {
        if (s == null) return Instant.now();
        final String iso = s.contains("T") ? s : s.replace(" ", "T") + "Z";
        return Instant.parse(iso);
    }
}
