package dev.rafex.insightbloom.survey.adapters.outbound.sqlite;

import dev.rafex.insightbloom.survey.domain.model.SurveyResponse;
import dev.rafex.insightbloom.survey.domain.ports.SurveyResponseRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class SqliteSurveyResponseRepository implements SurveyResponseRepository {
    private final DatabaseManager db;

    public SqliteSurveyResponseRepository(final DatabaseManager db) { this.db = db; }

    @Override
    public void save(final SurveyResponse r) {
        final String sql = """
            INSERT INTO survey_responses
                (uuid, conference_uuid, question_uuid, respondent_token, answer_text, answer_rating,
                 grade_score, grade_feedback, submitted_at, user_uuid)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, r.getUuid());
            ps.setString(2, r.getConferenceUuid());
            ps.setString(3, r.getQuestionUuid());
            ps.setString(4, r.getRespondentToken());
            ps.setString(5, r.getAnswerText());
            if (r.getAnswerRating() == null) ps.setNull(6, java.sql.Types.INTEGER);
            else ps.setInt(6, r.getAnswerRating());
            if (r.getGradeScore() == null) ps.setNull(7, java.sql.Types.REAL);
            else ps.setDouble(7, r.getGradeScore());
            ps.setString(8, r.getGradeFeedback());
            ps.setString(9, r.getSubmittedAt().toString());
            ps.setString(10, r.getUserUuid());
            ps.executeUpdate();
        } catch (final SQLException e) {
            throw new RuntimeException("Failed to save survey response", e);
        }
    }

    @Override
    public void updateGrade(final String responseUuid, final Double gradeScore, final String gradeFeedback) {
        final String sql = "UPDATE survey_responses SET grade_score = ?, grade_feedback = ? WHERE uuid = ?";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            if (gradeScore == null) ps.setNull(1, java.sql.Types.REAL);
            else ps.setDouble(1, gradeScore);
            ps.setString(2, gradeFeedback);
            ps.setString(3, responseUuid);
            ps.executeUpdate();
        } catch (final SQLException e) {
            throw new RuntimeException("Failed to update survey response grade", e);
        }
    }

    @Override
    public List<SurveyResponse> findByConference(final String conferenceUuid) {
        return query("SELECT * FROM survey_responses WHERE conference_uuid = ?", conferenceUuid);
    }

    @Override
    public List<SurveyResponse> findByQuestion(final String questionUuid) {
        return query("SELECT * FROM survey_responses WHERE question_uuid = ?", questionUuid);
    }

    @Override
    public void deleteByQuestion(final String questionUuid) {
        delete("DELETE FROM survey_responses WHERE question_uuid = ?", questionUuid);
    }

    @Override
    public void deleteByConference(final String conferenceUuid) {
        delete("DELETE FROM survey_responses WHERE conference_uuid = ?", conferenceUuid);
    }

    @Override
    public boolean existsByUserAndConference(final String conferenceUuid, final String userUuid) {
        if (userUuid == null || userUuid.isBlank()) return false;
        final String sql = "SELECT 1 FROM survey_responses WHERE conference_uuid = ? AND user_uuid = ? LIMIT 1";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, conferenceUuid);
            ps.setString(2, userUuid);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (final SQLException e) {
            throw new RuntimeException("Failed to check existing survey response", e);
        }
    }

    private void delete(final String sql, final String param) {
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, param);
            ps.executeUpdate();
        } catch (final SQLException e) {
            throw new RuntimeException("Failed to delete survey responses", e);
        }
    }

    private List<SurveyResponse> query(final String sql, final String param) {
        final List<SurveyResponse> result = new ArrayList<>();
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    final Object ratingObj = rs.getObject("answer_rating");
                    final Object scoreObj = rs.getObject("grade_score");
                    result.add(new SurveyResponse(
                            rs.getString("uuid"),
                            rs.getString("conference_uuid"),
                            rs.getString("question_uuid"),
                            rs.getString("respondent_token"),
                            rs.getString("answer_text"),
                            ratingObj == null ? null : ((Number) ratingObj).intValue(),
                            scoreObj == null ? null : ((Number) scoreObj).doubleValue(),
                            rs.getString("grade_feedback"),
                            Instant.parse(rs.getString("submitted_at")),
                            rs.getString("user_uuid")));
                }
            }
        } catch (final SQLException e) {
            throw new RuntimeException("Failed to query survey responses", e);
        }
        return result;
    }
}
