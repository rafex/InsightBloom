package dev.rafex.insightbloom.users.adapters.outbound.sqlite;

import dev.rafex.insightbloom.users.domain.model.CertificateSettings;
import dev.rafex.insightbloom.users.domain.ports.CertificateSettingsRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;

public class SqliteCertificateSettingsRepository implements CertificateSettingsRepository {
    private final DatabaseManager db;

    public SqliteCertificateSettingsRepository(final DatabaseManager db) { this.db = db; }

    @Override
    public CertificateSettings get() {
        final String sql = "SELECT * FROM certificate_settings WHERE id = 1";
        try (Connection c = db.getConnection(); Statement stmt = c.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (!rs.next()) return CertificateSettings.defaults();
            final CertificateSettings s = new CertificateSettings();
            s.setLogoBase64(rs.getString("logo_base64"));
            s.setFontFamily(rs.getString("font_family"));
            s.setTitleFontSize(rs.getInt("title_font_size"));
            s.setBodyFontSize(rs.getInt("body_font_size"));
            s.setPrimaryColorHex(rs.getString("primary_color_hex"));
            s.setShowVenue(rs.getInt("show_venue") == 1);
            s.setShowSchedule(rs.getInt("show_schedule") == 1);
            s.setShowIssuedDate(rs.getInt("show_issued_date") == 1);
            return s;
        } catch (final SQLException e) {
            throw new RuntimeException("Failed to load certificate settings", e);
        }
    }

    @Override
    public void save(final CertificateSettings s) {
        final String sql = """
            INSERT INTO certificate_settings
                (id, logo_base64, font_family, title_font_size, body_font_size, primary_color_hex,
                 show_venue, show_schedule, show_issued_date, updated_at)
            VALUES (1, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                logo_base64 = excluded.logo_base64,
                font_family = excluded.font_family,
                title_font_size = excluded.title_font_size,
                body_font_size = excluded.body_font_size,
                primary_color_hex = excluded.primary_color_hex,
                show_venue = excluded.show_venue,
                show_schedule = excluded.show_schedule,
                show_issued_date = excluded.show_issued_date,
                updated_at = excluded.updated_at
            """;
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, s.getLogoBase64());
            ps.setString(2, s.getFontFamily());
            ps.setInt(3, s.getTitleFontSize());
            ps.setInt(4, s.getBodyFontSize());
            ps.setString(5, s.getPrimaryColorHex());
            ps.setInt(6, s.isShowVenue() ? 1 : 0);
            ps.setInt(7, s.isShowSchedule() ? 1 : 0);
            ps.setInt(8, s.isShowIssuedDate() ? 1 : 0);
            ps.setString(9, Instant.now().toString());
            ps.executeUpdate();
        } catch (final SQLException e) {
            throw new RuntimeException("Failed to save certificate settings", e);
        }
    }
}
