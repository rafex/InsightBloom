package dev.rafex.insightbloom.users.adapters.outbound.sqlite;

import dev.rafex.insightbloom.users.domain.model.PlatformSettings;
import dev.rafex.insightbloom.users.domain.ports.PlatformSettingsRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Instant;

public class SqlitePlatformSettingsRepository implements PlatformSettingsRepository {
    private final DatabaseManager db;

    public SqlitePlatformSettingsRepository(final DatabaseManager db) { this.db = db; }

    @Override
    public PlatformSettings get() {
        final String sql = "SELECT * FROM platform_settings WHERE id = 1";
        try (Connection c = db.getConnection(); Statement stmt = c.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (!rs.next()) return PlatformSettings.defaults();
            final PlatformSettings s = new PlatformSettings();
            s.setChatAiEnabled(rs.getInt("chat_ai_enabled") == 1);
            s.setChatSystemPrompt(rs.getString("chat_system_prompt"));
            final double temperature = rs.getDouble("chat_temperature");
            s.setChatTemperature(rs.wasNull() ? null : temperature);
            return s;
        } catch (final SQLException e) {
            throw new RuntimeException("Failed to load platform settings", e);
        }
    }

    @Override
    public void save(final PlatformSettings s) {
        final String sql = """
            INSERT INTO platform_settings (id, chat_ai_enabled, chat_system_prompt, chat_temperature, updated_at)
            VALUES (1, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                chat_ai_enabled = excluded.chat_ai_enabled,
                chat_system_prompt = excluded.chat_system_prompt,
                chat_temperature = excluded.chat_temperature,
                updated_at = excluded.updated_at
            """;
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, s.isChatAiEnabled() ? 1 : 0);
            ps.setString(2, s.getChatSystemPrompt());
            if (s.getChatTemperature() != null) ps.setDouble(3, s.getChatTemperature());
            else ps.setNull(3, Types.REAL);
            ps.setString(4, Instant.now().toString());
            ps.executeUpdate();
        } catch (final SQLException e) {
            throw new RuntimeException("Failed to save platform settings", e);
        }
    }
}
