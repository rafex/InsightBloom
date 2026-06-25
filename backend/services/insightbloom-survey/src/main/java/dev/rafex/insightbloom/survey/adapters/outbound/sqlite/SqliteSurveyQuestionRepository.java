package dev.rafex.insightbloom.survey.adapters.outbound.sqlite;

import dev.rafex.insightbloom.survey.domain.model.QuestionType;
import dev.rafex.insightbloom.survey.domain.model.SurveyQuestion;
import dev.rafex.insightbloom.survey.domain.ports.SurveyQuestionRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class SqliteSurveyQuestionRepository implements SurveyQuestionRepository {
    private static final String OPTIONS_SEP = ";;";

    private final DatabaseManager db;

    public SqliteSurveyQuestionRepository(final DatabaseManager db) { this.db = db; }

    @Override
    public void save(final SurveyQuestion q) {
        final String sql = """
            INSERT INTO survey_questions
                (uuid, conference_uuid, text, type, options_json, reference_answer, rating_style, order_index,
                 active, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(uuid) DO UPDATE SET
                text = excluded.text,
                type = excluded.type,
                options_json = excluded.options_json,
                reference_answer = excluded.reference_answer,
                rating_style = excluded.rating_style,
                order_index = excluded.order_index,
                active = excluded.active,
                updated_at = excluded.updated_at
            """;
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, q.getUuid());
            ps.setString(2, q.getConferenceUuid());
            ps.setString(3, q.getText());
            ps.setString(4, q.getType().name());
            ps.setString(5, q.getOptions() == null ? null : String.join(OPTIONS_SEP, q.getOptions()));
            ps.setString(6, q.getReferenceAnswer());
            ps.setString(7, q.getRatingStyle());
            ps.setInt(8, q.getOrderIndex());
            ps.setInt(9, q.isActive() ? 1 : 0);
            ps.setString(10, q.getCreatedAt().toString());
            ps.setString(11, q.getUpdatedAt().toString());
            ps.executeUpdate();
        } catch (final SQLException e) {
            throw new RuntimeException("Failed to save survey question", e);
        }
    }

    @Override
    public Optional<SurveyQuestion> findByUuid(final String uuid) {
        final String sql = "SELECT * FROM survey_questions WHERE uuid = ?";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (final SQLException e) {
            throw new RuntimeException("Failed to find survey question", e);
        }
    }

    @Override
    public List<SurveyQuestion> findByConference(final String conferenceUuid, final boolean onlyActive) {
        final String sql = onlyActive
                ? "SELECT * FROM survey_questions WHERE conference_uuid = ? AND active = 1 ORDER BY order_index ASC"
                : "SELECT * FROM survey_questions WHERE conference_uuid = ? ORDER BY order_index ASC";
        final List<SurveyQuestion> result = new ArrayList<>();
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, conferenceUuid);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(map(rs));
            }
        } catch (final SQLException e) {
            throw new RuntimeException("Failed to list survey questions", e);
        }
        return result;
    }

    private SurveyQuestion map(final ResultSet rs) throws SQLException {
        final String optionsRaw = rs.getString("options_json");
        final List<String> options = (optionsRaw == null || optionsRaw.isBlank())
                ? null
                : Arrays.asList(optionsRaw.split(OPTIONS_SEP));
        return new SurveyQuestion(
                rs.getString("uuid"),
                rs.getString("conference_uuid"),
                rs.getString("text"),
                QuestionType.valueOf(rs.getString("type")),
                options,
                rs.getString("reference_answer"),
                rs.getString("rating_style"),
                rs.getInt("order_index"),
                rs.getInt("active") == 1,
                Instant.parse(rs.getString("created_at")),
                Instant.parse(rs.getString("updated_at")));
    }
}
