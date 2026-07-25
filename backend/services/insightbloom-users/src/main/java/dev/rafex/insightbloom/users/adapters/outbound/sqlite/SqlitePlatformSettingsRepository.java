package dev.rafex.insightbloom.users.adapters.outbound.sqlite;

import dev.rafex.insightbloom.users.domain.model.PlatformSettings;
import dev.rafex.insightbloom.users.domain.ports.PlatformSettingsRepository;
import dev.rafex.insightbloom.users.adapters.outbound.crypto.AiApiKeyCipher;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Instant;

public class SqlitePlatformSettingsRepository implements PlatformSettingsRepository {
    private final DatabaseManager db;
    private final AiApiKeyCipher aiApiKeyCipher;

    public SqlitePlatformSettingsRepository(final DatabaseManager db, final AiApiKeyCipher aiApiKeyCipher) {
        this.db = db;
        this.aiApiKeyCipher = aiApiKeyCipher;
    }

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
            s.setAiBaseUrl(defaultIfBlank(rs.getString("ai_base_url"), "https://api.groq.com/openai/v1"));
            s.setAiModel(defaultIfBlank(rs.getString("ai_model"), "openai/gpt-oss-120b"));
            s.setAiApiKey(aiApiKeyCipher.decrypt(rs.getString("ai_api_key_ciphertext")));
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

    private static String defaultIfBlank(final String value, final String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    @Override
    public void save(final PlatformSettings s) {
        final String sql = """
            INSERT INTO platform_settings
                (id, chat_ai_enabled, chat_system_prompt, chat_temperature, ai_base_url, ai_model,
                 ai_api_key_ciphertext, updated_at, max_accounts_per_device, max_sessions_per_user,
                 max_registrations_per_device_per_day)
            VALUES (1, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                chat_ai_enabled = excluded.chat_ai_enabled,
                chat_system_prompt = excluded.chat_system_prompt,
                chat_temperature = excluded.chat_temperature,
                ai_base_url = excluded.ai_base_url,
                ai_model = excluded.ai_model,
                ai_api_key_ciphertext = excluded.ai_api_key_ciphertext,
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
            ps.setString(4, s.getAiBaseUrl());
            ps.setString(5, s.getAiModel());
            ps.setString(6, aiApiKeyCipher.encrypt(s.getAiApiKey()));
            ps.setString(7, Instant.now().toString());
            if (s.getMaxAccountsPerDevice() != null) ps.setInt(8, s.getMaxAccountsPerDevice());
            else ps.setNull(8, Types.INTEGER);
            if (s.getMaxSessionsPerUser() != null) ps.setInt(9, s.getMaxSessionsPerUser());
            else ps.setNull(9, Types.INTEGER);
            if (s.getMaxRegistrationsPerDevicePerDay() != null) ps.setInt(10, s.getMaxRegistrationsPerDevicePerDay());
            else ps.setNull(10, Types.INTEGER);
            ps.executeUpdate();
        } catch (final SQLException e) {
            throw new RuntimeException("Failed to save platform settings", e);
        }
    }
}
