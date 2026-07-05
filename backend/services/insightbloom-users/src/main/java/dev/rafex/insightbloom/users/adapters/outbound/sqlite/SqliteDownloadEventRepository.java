package dev.rafex.insightbloom.users.adapters.outbound.sqlite;

import dev.rafex.insightbloom.users.domain.ports.DownloadEventRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;

public class SqliteDownloadEventRepository implements DownloadEventRepository {
    private final DatabaseManager db;

    public SqliteDownloadEventRepository(final DatabaseManager db) {
        this.db = db;
    }

    @Override
    public void record(final String conferenceUuid, final String kind) {
        final String sql = """
            INSERT INTO download_events (uuid, conference_uuid, kind, created_at)
            VALUES (?, ?, ?, ?)
        """;
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, UUID.randomUUID().toString());
            ps.setString(2, conferenceUuid);
            ps.setString(3, kind);
            ps.setString(4, Instant.now().toString());
            ps.executeUpdate();
        } catch (final SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public long countByConferenceAndKind(final String conferenceUuid, final String kind) {
        final String sql = "SELECT COUNT(*) FROM download_events WHERE conference_uuid = ? AND kind = ?";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, conferenceUuid);
            ps.setString(2, kind);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        } catch (final SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
