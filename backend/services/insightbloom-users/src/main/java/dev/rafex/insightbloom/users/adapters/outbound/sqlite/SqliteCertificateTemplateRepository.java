package dev.rafex.insightbloom.users.adapters.outbound.sqlite;

import dev.rafex.insightbloom.users.domain.model.CertificateTemplate;
import dev.rafex.insightbloom.users.domain.ports.CertificateTemplateRepository;

import java.sql.*;
import java.time.Instant;
import java.util.Optional;

public final class SqliteCertificateTemplateRepository implements CertificateTemplateRepository {
    private final DatabaseManager db;

    public SqliteCertificateTemplateRepository(final DatabaseManager db) { this.db = db; }

    @Override
    public Optional<CertificateTemplate> findByConferenceUuid(final String conferenceUuid) {
        final String sql = "SELECT * FROM certificate_templates WHERE conference_uuid = ?";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, conferenceUuid);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(read(rs));
            }
        } catch (SQLException e) { throw new RuntimeException("Failed to load certificate template", e); }
    }

    @Override
    public void save(final CertificateTemplate t) {
        final String sql = """
                INSERT INTO certificate_templates
                    (conference_uuid, template_key, template_name, engine, document_json, version, updated_by_user_uuid, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(conference_uuid) DO UPDATE SET
                    template_key=excluded.template_key, template_name=excluded.template_name,
                    engine=excluded.engine, document_json=excluded.document_json,
                    version=excluded.version, updated_by_user_uuid=excluded.updated_by_user_uuid,
                    updated_at=excluded.updated_at
                """;
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, t.getConferenceUuid());
            ps.setString(2, t.getTemplateKey());
            ps.setString(3, t.getTemplateName());
            ps.setString(4, t.getEngine());
            ps.setString(5, t.getDocumentJson());
            ps.setInt(6, t.getVersion());
            ps.setString(7, t.getUpdatedByUserUuid());
            ps.setString(8, t.getUpdatedAt().toString());
            ps.executeUpdate();
        } catch (SQLException e) { throw new RuntimeException("Failed to save certificate template", e); }
    }

    private static CertificateTemplate read(final ResultSet rs) throws SQLException {
        return new CertificateTemplate(rs.getString("conference_uuid"), rs.getString("template_key"),
                rs.getString("template_name"), rs.getString("engine"), rs.getString("document_json"),
                rs.getInt("version"), rs.getString("updated_by_user_uuid"), Instant.parse(rs.getString("updated_at")));
    }
}
