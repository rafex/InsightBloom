package dev.rafex.insightbloom.users.adapters.outbound.sqlite;

import dev.rafex.insightbloom.users.domain.model.PlatformSettings;
import dev.rafex.insightbloom.users.domain.model.AiProviderSettings;
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
            final AiProviderSettings chat = new AiProviderSettings(
                    true, rs.getInt("chat_ai_enabled") == 1,
                    defaultIfBlank(rs.getString("ai_base_url"), "https://api.groq.com/openai/v1"),
                    defaultIfBlank(rs.getString("ai_model"), "openai/gpt-oss-120b"),
                    aiApiKeyCipher.decrypt(rs.getString("ai_api_key_ciphertext")),
                    rs.getString("chat_system_prompt"), rs.getString("chat_guardrails"), null);
            final double temperature = rs.getDouble("chat_temperature");
            chat.setTemperature(rs.wasNull() ? null : temperature);
            s.setChatAi(chat);
            s.setTutorAi(readProvider(rs, "tutor", chat));
            s.setSurveyAi(readProvider(rs, "survey", chat));
            s.setSeatLayoutAi(readProvider(rs, "seat_layout", chat));
            s.setEmailAi(readProvider(rs, "email", chat));
            final int maxAccountsPerDevice = rs.getInt("max_accounts_per_device");
            s.setMaxAccountsPerDevice(rs.wasNull() ? null : maxAccountsPerDevice);
            final int maxSessionsPerUser = rs.getInt("max_sessions_per_user");
            s.setMaxSessionsPerUser(rs.wasNull() ? null : maxSessionsPerUser);
            final int maxRegistrationsPerDevicePerDay = rs.getInt("max_registrations_per_device_per_day");
            s.setMaxRegistrationsPerDevicePerDay(rs.wasNull() ? null : maxRegistrationsPerDevicePerDay);
            s.setEgressAllowedHosts(rs.getString("egress_allowed_hosts"));
            s.setEgressBlockedHosts(rs.getString("egress_blocked_hosts"));
            s.setImageAllowList(rs.getString("image_allow_list"));
            s.setImageBlockList(rs.getString("image_block_list"));
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
                (id, chat_ai_enabled, chat_system_prompt, chat_guardrails, chat_temperature, ai_base_url, ai_model,
                 ai_api_key_ciphertext, updated_at, max_accounts_per_device, max_sessions_per_user,
                 max_registrations_per_device_per_day, egress_allowed_hosts, egress_blocked_hosts,
                 image_allow_list, image_block_list)
            VALUES (1, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                chat_ai_enabled = excluded.chat_ai_enabled,
                chat_system_prompt = excluded.chat_system_prompt,
                chat_guardrails = excluded.chat_guardrails,
                chat_temperature = excluded.chat_temperature,
                ai_base_url = excluded.ai_base_url,
                ai_model = excluded.ai_model,
                ai_api_key_ciphertext = excluded.ai_api_key_ciphertext,
                updated_at = excluded.updated_at,
                max_accounts_per_device = excluded.max_accounts_per_device,
                max_sessions_per_user = excluded.max_sessions_per_user,
                max_registrations_per_device_per_day = excluded.max_registrations_per_device_per_day,
                egress_allowed_hosts = excluded.egress_allowed_hosts,
                egress_blocked_hosts = excluded.egress_blocked_hosts,
                image_allow_list = excluded.image_allow_list,
                image_block_list = excluded.image_block_list
            """;
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, s.isChatAiEnabled() ? 1 : 0);
            ps.setString(2, s.getChatSystemPrompt());
            ps.setString(3, s.getChatAi().getGuardrails());
            if (s.getChatTemperature() != null) ps.setDouble(4, s.getChatTemperature());
            else ps.setNull(4, Types.REAL);
            ps.setString(5, s.getAiBaseUrl());
            ps.setString(6, s.getAiModel());
            ps.setString(7, aiApiKeyCipher.encrypt(s.getAiApiKey()));
            ps.setString(8, Instant.now().toString());
            if (s.getMaxAccountsPerDevice() != null) ps.setInt(9, s.getMaxAccountsPerDevice());
            else ps.setNull(9, Types.INTEGER);
            if (s.getMaxSessionsPerUser() != null) ps.setInt(10, s.getMaxSessionsPerUser());
            else ps.setNull(10, Types.INTEGER);
            if (s.getMaxRegistrationsPerDevicePerDay() != null) ps.setInt(11, s.getMaxRegistrationsPerDevicePerDay());
            else ps.setNull(11, Types.INTEGER);
            ps.setString(12, s.getEgressAllowedHosts());
            ps.setString(13, s.getEgressBlockedHosts());
            ps.setString(14, s.getImageAllowList());
            ps.setString(15, s.getImageBlockList());
            ps.executeUpdate();
            saveProvider(c, "tutor", s.getTutorAi());
            saveProvider(c, "survey", s.getSurveyAi());
            saveProvider(c, "seat_layout", s.getSeatLayoutAi());
            saveProvider(c, "email", s.getEmailAi());
        } catch (final SQLException e) {
            throw new RuntimeException("Failed to save platform settings", e);
        }
    }

    private AiProviderSettings readProvider(final ResultSet rs, final String capability,
                                             final AiProviderSettings legacyFallback) throws SQLException {
        if (rs.getInt(capability + "_ai_configured") != 1) {
            final AiProviderSettings inherited = legacyFallback.copy();
            inherited.setConfigured(false);
            return inherited;
        }
        final double temperature = rs.getDouble(capability + "_ai_temperature");
        return new AiProviderSettings(
                true,
                rs.getInt(capability + "_ai_enabled") == 1,
                defaultIfBlank(rs.getString(capability + "_ai_base_url"), "https://api.groq.com/openai/v1"),
                defaultIfBlank(rs.getString(capability + "_ai_model"), "openai/gpt-oss-120b"),
                aiApiKeyCipher.decrypt(rs.getString(capability + "_ai_api_key_ciphertext")),
                rs.getString(capability + "_ai_system_prompt"),
                rs.getString(capability + "_ai_guardrails"),
                rs.wasNull() ? null : temperature);
    }

    private void saveProvider(final Connection c, final String capability,
                              final AiProviderSettings settings) throws SQLException {
        final String sql = "UPDATE platform_settings SET "
                + capability + "_ai_configured = ?, "
                + capability + "_ai_enabled = ?, "
                + capability + "_ai_base_url = ?, "
                + capability + "_ai_model = ?, "
                + capability + "_ai_api_key_ciphertext = ?, "
                + capability + "_ai_system_prompt = ?, "
                + capability + "_ai_guardrails = ?, "
                + capability + "_ai_temperature = ? WHERE id = 1";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, settings.isConfigured() ? 1 : 0);
            ps.setInt(2, settings.isEnabled() ? 1 : 0);
            ps.setString(3, settings.getBaseUrl());
            ps.setString(4, settings.getModel());
            ps.setString(5, aiApiKeyCipher.encrypt(settings.getApiKey()));
            ps.setString(6, settings.getSystemPrompt());
            ps.setString(7, settings.getGuardrails());
            if (settings.getTemperature() != null) ps.setDouble(8, settings.getTemperature());
            else ps.setNull(8, Types.REAL);
            ps.executeUpdate();
        }
    }
}
