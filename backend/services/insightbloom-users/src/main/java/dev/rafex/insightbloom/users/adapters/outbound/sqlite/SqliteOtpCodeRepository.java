package dev.rafex.insightbloom.users.adapters.outbound.sqlite;

import dev.rafex.insightbloom.users.domain.model.OtpChannel;
import dev.rafex.insightbloom.users.domain.model.OtpCode;
import dev.rafex.insightbloom.users.domain.ports.OtpCodeRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;

public class SqliteOtpCodeRepository implements OtpCodeRepository {
    private final DatabaseManager db;

    public SqliteOtpCodeRepository(final DatabaseManager db) { this.db = db; }

    @Override
    public void save(final OtpCode otpCode) {
        final String sql = """
            INSERT INTO otp_codes (uuid, identifier, channel, code, expires_at, consumed, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """;
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, otpCode.getUuid());
            ps.setString(2, otpCode.getIdentifier());
            ps.setString(3, otpCode.getChannel().name());
            ps.setString(4, otpCode.getCode());
            ps.setString(5, otpCode.getExpiresAt().toString());
            ps.setInt(6, otpCode.isConsumed() ? 1 : 0);
            ps.setString(7, otpCode.getCreatedAt().toString());
            ps.executeUpdate();
        } catch (final SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Optional<OtpCode> findLatestActive(final String identifier) {
        final String sql = """
            SELECT * FROM otp_codes WHERE identifier = ? AND consumed = 0
            ORDER BY created_at DESC LIMIT 1
        """;
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, identifier);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(map(rs));
            }
        } catch (final SQLException e) {
            throw new RuntimeException(e);
        }
        return Optional.empty();
    }

    @Override
    public void markConsumed(final String uuid) {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("UPDATE otp_codes SET consumed = 1 WHERE uuid = ?")) {
            ps.setString(1, uuid);
            ps.executeUpdate();
        } catch (final SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private OtpCode map(final ResultSet rs) throws SQLException {
        return new OtpCode(
                rs.getString("uuid"), rs.getString("identifier"), OtpChannel.valueOf(rs.getString("channel")),
                rs.getString("code"), Instant.parse(rs.getString("expires_at")),
                rs.getInt("consumed") == 1, Instant.parse(rs.getString("created_at")));
    }
}
