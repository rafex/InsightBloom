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
            final int maxAccountsPerDevice = rs.getInt("max_accounts_per_device");
            s.setMaxAccountsPerDevice(rs.wasNull() ? null : maxAccountsPerDevice);
            final int maxSessionsPerUser = rs.getInt("max_sessions_per_user");
            s.setMaxSessionsPerUser(rs.wasNull() ? null : maxSessionsPerUser);
            final int maxRegistrationsPerDevicePerDay = rs.getInt("max_registrations_per_device_per_day");
            s.setMaxRegistrationsPerDevicePerDay(rs.wasNull() ? null : maxRegistrationsPerDevicePerDay);
            return s;
        } catch (final SQLException e) {
            throw new RuntimeException("Failed to load platform settings", e);
        }
    }

    @Override
    public void save(final PlatformSettings s) {
        final String sql = """
            INSERT INTO platform_settings
                (id, chat_ai_enabled, chat_system_prompt, chat_temperature, updated_at,
                 max_accounts_per_device, max_sessions_per_user, max_registrations_per_device_per_day)
            VALUES (1, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                chat_ai_enabled = excluded.chat_ai_enabled,
                chat_system_prompt = excluded.chat_system_prompt,
                chat_temperature = excluded.chat_temperature,
                updated_at = excluded.updated_at,
                max_accounts_per_device = excluded.max_accounts_per_device,
                max_sessions_per_user = excluded.max_sessions_per_user,
                max_registrations_per_device_per_day = excluded.max_registrations_per_device_per_day
            """;
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, s.isChatAiEnabled() ? 1 : 0);
            ps.setString(2, s.getChatSystemPrompt());
            if (s.getChatTemperature() != null) ps.setDouble(3, s.getChatTemperature());
            else ps.setNull(3, Types.REAL);
            ps.setString(4, Instant.now().toString());
            if (s.getMaxAccountsPerDevice() != null) ps.setInt(5, s.getMaxAccountsPerDevice());
            else ps.setNull(5, Types.INTEGER);
            if (s.getMaxSessionsPerUser() != null) ps.setInt(6, s.getMaxSessionsPerUser());
            else ps.setNull(6, Types.INTEGER);
            if (s.getMaxRegistrationsPerDevicePerDay() != null) ps.setInt(7, s.getMaxRegistrationsPerDevicePerDay());
            else ps.setNull(7, Types.INTEGER);
            ps.executeUpdate();
        } catch (final SQLException e) {
            throw new RuntimeException("Failed to save platform settings", e);
        }
    }
}
