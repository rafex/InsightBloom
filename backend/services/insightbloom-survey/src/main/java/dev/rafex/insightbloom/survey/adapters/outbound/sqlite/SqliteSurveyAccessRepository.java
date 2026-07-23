package dev.rafex.insightbloom.survey.adapters.outbound.sqlite;

import dev.rafex.insightbloom.survey.domain.ports.SurveyAccessRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

public class SqliteSurveyAccessRepository implements SurveyAccessRepository {
    private static final String ALL_USERS = "*";
    private final DatabaseManager db;

    public SqliteSurveyAccessRepository(final DatabaseManager db) { this.db = db; }

    @Override
    public boolean isReleased(final String conferenceUuid, final String userUuid) {
        final String sql = "SELECT 1 FROM survey_access_releases WHERE conference_uuid = ? "
                + "AND (user_uuid = ? OR user_uuid = ?) LIMIT 1";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, conferenceUuid);
            ps.setString(2, userUuid);
            ps.setString(3, ALL_USERS);
            try (var rs = ps.executeQuery()) { return rs.next(); }
        } catch (final SQLException e) {
            throw new RuntimeException("Failed to check survey access", e);
        }
    }

    @Override
    public boolean isReleasedForAll(final String conferenceUuid) {
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(
                "SELECT 1 FROM survey_access_releases WHERE conference_uuid = ? AND user_uuid = ? LIMIT 1")) {
            ps.setString(1, conferenceUuid);
            ps.setString(2, ALL_USERS);
            try (var rs = ps.executeQuery()) { return rs.next(); }
        } catch (final SQLException e) {
            throw new RuntimeException("Failed to check global survey access", e);
        }
    }

    @Override
    public void releaseForAll(final String conferenceUuid) {
        releaseUsers(conferenceUuid, List.of(ALL_USERS));
    }

    @Override
    public void releaseUsers(final String conferenceUuid, final List<String> userUuids) {
        if (userUuids == null || userUuids.isEmpty()) return;
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(
                "INSERT OR IGNORE INTO survey_access_releases(conference_uuid, user_uuid, released_at) VALUES (?, ?, ?)")) {
            for (final String userUuid : userUuids) {
                ps.setString(1, conferenceUuid);
                ps.setString(2, userUuid);
                ps.setString(3, java.time.Instant.now().toString());
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (final SQLException e) {
            throw new RuntimeException("Failed to release survey access", e);
        }
    }

    @Override
    public void deleteByConference(final String conferenceUuid) {
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(
                "DELETE FROM survey_access_releases WHERE conference_uuid = ?")) {
            ps.setString(1, conferenceUuid);
            ps.executeUpdate();
        } catch (final SQLException e) {
            throw new RuntimeException("Failed to delete survey access", e);
        }
    }
}
