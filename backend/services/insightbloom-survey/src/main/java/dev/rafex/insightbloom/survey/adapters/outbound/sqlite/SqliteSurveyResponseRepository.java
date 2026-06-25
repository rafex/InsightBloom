package dev.rafex.insightbloom.survey.adapters.outbound.sqlite;

import dev.rafex.insightbloom.survey.domain.model.SurveyResponse;
import dev.rafex.insightbloom.survey.domain.ports.SurveyResponseRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class SqliteSurveyResponseRepository implements SurveyResponseRepository {
    private final DatabaseManager db;

    public SqliteSurveyResponseRepository(final DatabaseManager db) { this.db = db; }

    @Override
    public void save(final SurveyResponse r) {
        final String sql = """
            INSERT INTO survey_responses
                (uuid, conference_uuid, question_uuid, respondent_token, answer_text, answer_rating, submitted_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, r.getUuid());
            ps.setString(2, r.getConferenceUuid());
            ps.setString(3, r.getQuestionUuid());
            ps.setString(4, r.getRespondentToken());
            ps.setString(5, r.getAnswerText());
            if (r.getAnswerRating() == null) ps.setNull(6, java.sql.Types.INTEGER);
            else ps.setInt(6, r.getAnswerRating());
            ps.setString(7, r.getSubmittedAt().toString());
            ps.executeUpdate();
        } catch (final SQLException e) {
            throw new RuntimeException("Failed to save survey response", e);
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

    private List<SurveyResponse> query(final String sql, final String param) {
        final List<SurveyResponse> result = new ArrayList<>();
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    final Object ratingObj = rs.getObject("answer_rating");
                    result.add(new SurveyResponse(
                            rs.getString("uuid"),
                            rs.getString("conference_uuid"),
                            rs.getString("question_uuid"),
                            rs.getString("respondent_token"),
                            rs.getString("answer_text"),
                            ratingObj == null ? null : ((Number) ratingObj).intValue()));
                }
            }
        } catch (final SQLException e) {
            throw new RuntimeException("Failed to query survey responses", e);
        }
        return result;
    }
}
