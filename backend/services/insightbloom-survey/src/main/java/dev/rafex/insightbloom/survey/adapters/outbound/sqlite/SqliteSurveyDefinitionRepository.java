package dev.rafex.insightbloom.survey.adapters.outbound.sqlite;

import dev.rafex.insightbloom.survey.domain.model.SurveyDefinition;
import dev.rafex.insightbloom.survey.domain.model.SurveyEngine;
import dev.rafex.insightbloom.survey.domain.ports.SurveyDefinitionRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;

public class SqliteSurveyDefinitionRepository implements SurveyDefinitionRepository {
    private final DatabaseManager db;

    public SqliteSurveyDefinitionRepository(final DatabaseManager db) { this.db = db; }

    @Override
    public Optional<SurveyDefinition> findByConference(final String conferenceUuid) {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT * FROM survey_definitions WHERE conference_uuid = ?")) {
            ps.setString(1, conferenceUuid);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (final SQLException e) {
            throw new RuntimeException("Failed to find survey definition", e);
        }
    }

    @Override
    public void save(final SurveyDefinition definition) {
        final String sql = """
                INSERT INTO survey_definitions
                    (uuid, conference_uuid, engine, schema_json, schema_version, status,
                     created_at, updated_at, published_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(conference_uuid) DO UPDATE SET
                    schema_json = excluded.schema_json,
                    schema_version = excluded.schema_version,
                    status = excluded.status,
                    updated_at = excluded.updated_at,
                    published_at = excluded.published_at
                """;
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, definition.getUuid());
            ps.setString(2, definition.getConferenceUuid());
            ps.setString(3, definition.getEngine().name());
            ps.setString(4, definition.getSchemaJson());
            ps.setInt(5, definition.getSchemaVersion());
            ps.setString(6, definition.getStatus());
            ps.setString(7, definition.getCreatedAt().toString());
            ps.setString(8, definition.getUpdatedAt().toString());
            ps.setString(9, definition.getPublishedAt() == null ? null : definition.getPublishedAt().toString());
            ps.executeUpdate();
        } catch (final SQLException e) {
            throw new RuntimeException("Failed to save survey definition", e);
        }
    }

    @Override
    public void deleteByConference(final String conferenceUuid) {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM survey_definitions WHERE conference_uuid = ?")) {
            ps.setString(1, conferenceUuid);
            ps.executeUpdate();
        } catch (final SQLException e) {
            throw new RuntimeException("Failed to delete survey definition", e);
        }
    }

    private SurveyDefinition map(final ResultSet rs) throws SQLException {
        final String publishedAt = rs.getString("published_at");
        return new SurveyDefinition(rs.getString("uuid"), rs.getString("conference_uuid"),
                SurveyEngine.valueOf(rs.getString("engine")), rs.getString("schema_json"),
                rs.getInt("schema_version"), rs.getString("status"),
                Instant.parse(rs.getString("created_at")), Instant.parse(rs.getString("updated_at")),
                publishedAt == null ? null : Instant.parse(publishedAt));
    }
}
