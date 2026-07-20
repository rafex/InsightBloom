package dev.rafex.insightbloom.users.adapters.outbound.sqlite;

import dev.rafex.insightbloom.users.domain.model.DeviceFingerprintFlag;
import dev.rafex.insightbloom.users.domain.ports.DeviceFingerprintFlagRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class SqliteDeviceFingerprintFlagRepository implements DeviceFingerprintFlagRepository {
    private final DatabaseManager db;

    public SqliteDeviceFingerprintFlagRepository(final DatabaseManager db) {
        this.db = db;
    }

    @Override
    public void recordMismatch(final String tokenUuid, final String subjectUuid, final String subjectKind,
                                final String loginFingerprint, final String requestFingerprint) {
        final String sql = """
            INSERT INTO device_fingerprint_flags
                (uuid, token_uuid, subject_uuid, subject_kind, login_fingerprint, last_seen_fingerprint,
                 occurrence_count, first_seen_at, last_seen_at)
            VALUES (?, ?, ?, ?, ?, ?, 1, ?, ?)
            ON CONFLICT(token_uuid) DO UPDATE SET
                last_seen_fingerprint = excluded.last_seen_fingerprint,
                occurrence_count = occurrence_count + 1,
                last_seen_at = excluded.last_seen_at
        """;
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            final String now = Instant.now().toString();
            ps.setString(1, UUID.randomUUID().toString());
            ps.setString(2, tokenUuid);
            ps.setString(3, subjectUuid);
            ps.setString(4, subjectKind);
            ps.setString(5, loginFingerprint);
            ps.setString(6, requestFingerprint);
            ps.setString(7, now);
            ps.setString(8, now);
            ps.executeUpdate();
        } catch (final SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<DeviceFingerprintFlag> findAll() {
        final String sql = "SELECT * FROM device_fingerprint_flags ORDER BY last_seen_at DESC";
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            final List<DeviceFingerprintFlag> result = new ArrayList<>();
            while (rs.next()) result.add(map(rs));
            return result;
        } catch (final SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void markReviewed(final String uuid, final String reviewedByUserUuid) {
        final String sql = """
            UPDATE device_fingerprint_flags SET reviewed_at = ?, reviewed_by = ?
            WHERE uuid = ? AND reviewed_at IS NULL
        """;
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, Instant.now().toString());
            ps.setString(2, reviewedByUserUuid);
            ps.setString(3, uuid);
            ps.executeUpdate();
        } catch (final SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private static DeviceFingerprintFlag map(final ResultSet rs) throws SQLException {
        final String reviewedStr = rs.getString("reviewed_at");
        return new DeviceFingerprintFlag(
            rs.getString("uuid"), rs.getString("token_uuid"), rs.getString("subject_uuid"),
            rs.getString("subject_kind"), rs.getString("login_fingerprint"), rs.getString("last_seen_fingerprint"),
            rs.getInt("occurrence_count"), parseInstant(rs.getString("first_seen_at")),
            parseInstant(rs.getString("last_seen_at")),
            reviewedStr != null ? parseInstant(reviewedStr) : null, rs.getString("reviewed_by")
        );
    }

    private static Instant parseInstant(final String s) {
        if (s == null) return Instant.now();
        final String iso = s.contains("T") ? s : s.replace(" ", "T") + "Z";
        return Instant.parse(iso);
    }
}
