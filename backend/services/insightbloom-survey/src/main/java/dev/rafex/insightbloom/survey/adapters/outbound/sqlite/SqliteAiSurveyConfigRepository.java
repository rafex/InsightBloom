package dev.rafex.insightbloom.survey.adapters.outbound.sqlite;

import dev.rafex.insightbloom.survey.domain.model.AiSurveyConfig;
import dev.rafex.insightbloom.survey.domain.ports.AiSurveyConfigRepository;

import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;

public class SqliteAiSurveyConfigRepository implements AiSurveyConfigRepository {
    private final DatabaseManager database;

    public SqliteAiSurveyConfigRepository(final DatabaseManager database) {
        this.database = database;
    }

    @Override
    public Optional<AiSurveyConfig> findByConference(final String conferenceUuid) {
        try (var c = database.getConnection(); var ps = c.prepareStatement("""
                SELECT conference_uuid, extra_context, updated_at
                FROM ai_survey_configs WHERE conference_uuid = ?""")) {
            ps.setString(1, conferenceUuid);
            try (var rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(read(rs));
            }
        } catch (final SQLException e) {
            throw new RuntimeException("Failed to read AI survey config", e);
        }
    }

    @Override
    public AiSurveyConfig save(final AiSurveyConfig config) {
        try (var c = database.getConnection(); var ps = c.prepareStatement("""
                INSERT INTO ai_survey_configs (conference_uuid, extra_context, updated_at)
                VALUES (?, ?, ?)
                ON CONFLICT(conference_uuid) DO UPDATE SET
                    extra_context = excluded.extra_context,
                    updated_at = excluded.updated_at""")) {
            ps.setString(1, config.conferenceUuid());
            ps.setString(2, config.extraContext());
            ps.setString(3, config.updatedAt().toString());
            ps.executeUpdate();
            return config;
        } catch (final SQLException e) {
            throw new RuntimeException("Failed to save AI survey config", e);
        }
    }

    @Override
    public void deleteByConference(final String conferenceUuid) {
        try (var c = database.getConnection(); var ps = c.prepareStatement(
                "DELETE FROM ai_survey_configs WHERE conference_uuid = ?")) {
            ps.setString(1, conferenceUuid);
            ps.executeUpdate();
        } catch (final SQLException e) {
            throw new RuntimeException("Failed to delete AI survey config", e);
        }
    }

    private AiSurveyConfig read(final java.sql.ResultSet rs) throws SQLException {
        return new AiSurveyConfig(
                rs.getString("conference_uuid"),
                rs.getString("extra_context"),
                Instant.parse(rs.getString("updated_at")));
    }
}
