package dev.rafex.insightbloom.users.adapters.outbound.sqlite;

import dev.rafex.insightbloom.users.domain.model.GuestUser;
import dev.rafex.insightbloom.users.domain.ports.GuestUserRepository;

import java.sql.*;
import java.time.Instant;
import java.util.Optional;

public class SqliteGuestUserRepository implements GuestUserRepository {
    private final DatabaseManager db;

    public SqliteGuestUserRepository(DatabaseManager db) {
        this.db = db;
    }

    @Override
    public void save(GuestUser guest) {
        String sql = """
            INSERT OR REPLACE INTO guest_users (uuid, display_name, device_fingerprint, conference_uuid, created_at)
            VALUES (?, ?, ?, ?, ?)
        """;
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, guest.getUuid());
            ps.setString(2, guest.getDisplayName());
            ps.setString(3, guest.getDeviceFingerprint());
            ps.setString(4, guest.getConferenceUuid());
            ps.setString(5, guest.getCreatedAt().toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Optional<GuestUser> findByUuid(String uuid) {
        String sql = "SELECT * FROM guest_users WHERE uuid = ?";
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return Optional.of(new GuestUser(
                    rs.getString("uuid"), rs.getString("display_name"),
                    rs.getString("device_fingerprint"), rs.getString("conference_uuid"),
                    Instant.parse(rs.getString("created_at"))
                ));
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return Optional.empty();
    }
}
