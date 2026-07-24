package dev.rafex.insightbloom.survey.adapters.outbound.sqlite;

import dev.rafex.insightbloom.survey.domain.model.AiMentorConfig;
import dev.rafex.insightbloom.survey.domain.ports.AiMentorConfigRepository;

import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;

public class SqliteAiMentorConfigRepository implements AiMentorConfigRepository {
    private final DatabaseManager database;

    public SqliteAiMentorConfigRepository(final DatabaseManager database) {
        this.database = database;
    }

    @Override
    public Optional<AiMentorConfig> findByConference(final String conferenceUuid) {
        try (var c = database.getConnection(); var ps = c.prepareStatement("""
                SELECT conference_uuid, enabled, objective, prompt, include_presentation,
                       max_requests_per_minute, updated_at
                FROM ai_mentor_configs WHERE conference_uuid = ?""")) {
            ps.setString(1, conferenceUuid);
            try (var rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(read(rs));
            }
        } catch (final SQLException e) {
            throw new RuntimeException("Failed to read AI mentor config", e);
        }
    }

    @Override
    public AiMentorConfig save(final AiMentorConfig config) {
        try (var c = database.getConnection(); var ps = c.prepareStatement("""
                INSERT INTO ai_mentor_configs
                    (conference_uuid, enabled, objective, prompt, include_presentation,
                     max_requests_per_minute, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(conference_uuid) DO UPDATE SET
                    enabled = excluded.enabled,
                    objective = excluded.objective,
                    prompt = excluded.prompt,
                    include_presentation = excluded.include_presentation,
                    max_requests_per_minute = excluded.max_requests_per_minute,
                    updated_at = excluded.updated_at""")) {
            ps.setString(1, config.conferenceUuid());
            ps.setInt(2, config.enabled() ? 1 : 0);
            ps.setString(3, config.objective());
            ps.setString(4, config.prompt());
            ps.setInt(5, config.includePresentation() ? 1 : 0);
            ps.setInt(6, config.maxRequestsPerMinute());
            ps.setString(7, config.updatedAt().toString());
            ps.executeUpdate();
            return config;
        } catch (final SQLException e) {
            throw new RuntimeException("Failed to save AI mentor config", e);
        }
    }

    @Override
    public void deleteByConference(final String conferenceUuid) {
        try (var c = database.getConnection(); var ps = c.prepareStatement(
                "DELETE FROM ai_mentor_configs WHERE conference_uuid = ?")) {
            ps.setString(1, conferenceUuid);
            ps.executeUpdate();
        } catch (final SQLException e) {
            throw new RuntimeException("Failed to delete AI mentor config", e);
        }
    }

    private AiMentorConfig read(final java.sql.ResultSet rs) throws SQLException {
        return new AiMentorConfig(
                rs.getString("conference_uuid"),
                rs.getInt("enabled") != 0,
                rs.getString("objective"),
                rs.getString("prompt"),
                rs.getInt("include_presentation") != 0,
                rs.getInt("max_requests_per_minute"),
                Instant.parse(rs.getString("updated_at")));
    }
}
