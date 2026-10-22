package dev.rafex.insightbloom.users.adapters.outbound.sqlite;

import dev.rafex.insightbloom.users.domain.model.PlatformSettings;
import dev.rafex.insightbloom.users.domain.ports.PlatformSettingsRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;

public class SqlitePlatformSettingsRepository implements PlatformSettingsRepository {
    private final DatabaseManager db;
    public SqlitePlatformSettingsRepository(final DatabaseManager db) {
        this.db = db;
    }

    @Override
    public PlatformSettings get() {
        final String sql = "SELECT max_accounts_per_device, max_sessions_per_user, "
                + "max_registrations_per_device_per_day, egress_allowed_hosts, egress_blocked_hosts, "
                + "image_allow_list, image_block_list FROM platform_settings WHERE id = 1";
        try (Connection c = db.getConnection(); Statement stmt = c.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (!rs.next()) return PlatformSettings.defaults();
            final PlatformSettings s = new PlatformSettings();
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

    @Override
    public void save(final PlatformSettings s) {
        final String sql = """
            INSERT INTO platform_settings
                (id, chat_ai_enabled, updated_at, max_accounts_per_device, max_sessions_per_user,
                 max_registrations_per_device_per_day, egress_allowed_hosts, egress_blocked_hosts,
                 image_allow_list, image_block_list)
            VALUES (1, 0, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                chat_ai_enabled = 0,
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
            ps.setString(1, Instant.now().toString());
            if (s.getMaxAccountsPerDevice() != null) ps.setInt(2, s.getMaxAccountsPerDevice());
            else ps.setNull(2, java.sql.Types.INTEGER);
            if (s.getMaxSessionsPerUser() != null) ps.setInt(3, s.getMaxSessionsPerUser());
            else ps.setNull(3, java.sql.Types.INTEGER);
            if (s.getMaxRegistrationsPerDevicePerDay() != null) ps.setInt(4, s.getMaxRegistrationsPerDevicePerDay());
            else ps.setNull(4, java.sql.Types.INTEGER);
            ps.setString(5, s.getEgressAllowedHosts());
            ps.setString(6, s.getEgressBlockedHosts());
            ps.setString(7, s.getImageAllowList());
            ps.setString(8, s.getImageBlockList());
            ps.executeUpdate();
        } catch (final SQLException e) {
            throw new RuntimeException("Failed to save platform settings", e);
        }
    }

}
