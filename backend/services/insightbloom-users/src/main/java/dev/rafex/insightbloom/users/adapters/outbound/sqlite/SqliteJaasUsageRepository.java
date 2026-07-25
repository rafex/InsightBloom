package dev.rafex.insightbloom.users.adapters.outbound.sqlite;

import dev.rafex.insightbloom.users.domain.ports.JaasUsageRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class SqliteJaasUsageRepository implements JaasUsageRepository {
    private final DatabaseManager db;

    public SqliteJaasUsageRepository(final DatabaseManager db) {
        this.db = db;
    }

    @Override
    public void recordUniqueParticipant(final String month, final String userUuid) {
        final String sql = """
            INSERT OR IGNORE INTO jaas_monthly_participants(month, user_uuid, first_seen_at)
            VALUES (?, ?, CURRENT_TIMESTAMP)
        """;
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, month);
            ps.setString(2, userUuid);
            ps.executeUpdate();
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public int countUniqueParticipants(final String month) {
        try (Connection conn = db.getConnection();
            PreparedStatement ps = conn.prepareStatement(
                     "SELECT COUNT(*) FROM jaas_monthly_participants WHERE month = ?")) {
            ps.setString(1, month);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }
}
