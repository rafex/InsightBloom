package dev.rafex.insightbloom.survey.adapters.outbound.sqlite;

import dev.rafex.insightbloom.survey.domain.model.SurveyJsSubmission;
import dev.rafex.insightbloom.survey.domain.ports.SurveyJsSubmissionRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class SqliteSurveyJsSubmissionRepository implements SurveyJsSubmissionRepository {
    private final DatabaseManager db;

    public SqliteSurveyJsSubmissionRepository(final DatabaseManager db) { this.db = db; }

    @Override
    public void save(final SurveyJsSubmission submission) {
        final String sql = """
                INSERT INTO survey_submissions
                    (uuid, conference_uuid, definition_uuid, definition_version, user_uuid,
                     payload_json, submitted_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, submission.uuid());
            ps.setString(2, submission.conferenceUuid());
            ps.setString(3, submission.definitionUuid());
            ps.setInt(4, submission.definitionVersion());
            ps.setString(5, submission.userUuid());
            ps.setString(6, submission.payloadJson());
            ps.setString(7, submission.submittedAt().toString());
            ps.executeUpdate();
        } catch (final SQLException e) {
            if (e.getMessage() != null && e.getMessage().contains("UNIQUE")) {
                throw new IllegalStateException("already_responded");
            }
            throw new RuntimeException("Failed to save SurveyJS submission", e);
        }
    }

    @Override
    public boolean existsByUserAndConference(final String conferenceUuid, final String userUuid) {
        if (userUuid == null || userUuid.isBlank()) return false;
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(
                "SELECT 1 FROM survey_submissions WHERE conference_uuid = ? AND user_uuid = ? LIMIT 1")) {
            ps.setString(1, conferenceUuid);
            ps.setString(2, userUuid);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        } catch (final SQLException e) {
            throw new RuntimeException("Failed to check SurveyJS submission", e);
        }
    }

    @Override
    public List<SurveyJsSubmission> findByConference(final String conferenceUuid) {
        final List<SurveyJsSubmission> result = new ArrayList<>();
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(
                "SELECT * FROM survey_submissions WHERE conference_uuid = ? ORDER BY submitted_at DESC")) {
            ps.setString(1, conferenceUuid);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(new SurveyJsSubmission(rs.getString("uuid"),
                        rs.getString("conference_uuid"), rs.getString("definition_uuid"),
                        rs.getInt("definition_version"), rs.getString("user_uuid"),
                        rs.getString("payload_json"), Instant.parse(rs.getString("submitted_at"))));
            }
        } catch (final SQLException e) {
            throw new RuntimeException("Failed to list SurveyJS submissions", e);
        }
        return result;
    }

    @Override
    public void deleteByConference(final String conferenceUuid) {
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(
                "DELETE FROM survey_submissions WHERE conference_uuid = ?")) {
            ps.setString(1, conferenceUuid);
            ps.executeUpdate();
        } catch (final SQLException e) {
            throw new RuntimeException("Failed to delete SurveyJS submissions", e);
        }
    }
}
