package dev.rafex.insightbloom.users.adapters.outbound.sqlite;

import dev.rafex.insightbloom.users.domain.model.User;
import dev.rafex.insightbloom.users.domain.model.UserRole;
import dev.rafex.insightbloom.users.domain.model.UserStatus;
import dev.rafex.insightbloom.users.domain.ports.UserRepository;

import java.sql.*;
import java.time.Instant;
import java.util.Optional;

public class SqliteUserRepository implements UserRepository {
    private final DatabaseManager db;

    public SqliteUserRepository(DatabaseManager db) {
        this.db = db;
    }

    @Override
    public void save(User user) {
        String sql = """
            INSERT OR REPLACE INTO users (uuid, username, display_name, email, role, status, password_hash, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, user.getUuid());
            ps.setString(2, user.getUsername());
            ps.setString(3, user.getDisplayName());
            ps.setString(4, user.getEmail());
            ps.setString(5, user.getRole().name());
            ps.setString(6, user.getStatus().name());
            ps.setString(7, user.getPasswordHash());
            ps.setString(8, user.getCreatedAt().toString());
            ps.setString(9, user.getUpdatedAt().toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Optional<User> findByUuid(String uuid) {
        String sql = "SELECT * FROM users WHERE uuid = ?";
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return Optional.of(map(rs));
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return Optional.empty();
    }

    @Override
    public Optional<User> findByUsername(String username) {
        String sql = "SELECT * FROM users WHERE username = ?";
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return Optional.of(map(rs));
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return Optional.empty();
    }

    private User map(ResultSet rs) throws SQLException {
        return new User(
            rs.getString("id"), rs.getString("uuid"), rs.getString("username"),
            rs.getString("display_name"), rs.getString("email"),
            UserRole.valueOf(rs.getString("role")), UserStatus.valueOf(rs.getString("status")),
            rs.getString("password_hash"),
            parseInstant(rs.getString("created_at")), parseInstant(rs.getString("updated_at"))
        );
    }

    private static Instant parseInstant(String s) {
        if (s == null) return Instant.now();
        String iso = s.contains("T") ? s : s.replace(" ", "T") + "Z";
        return Instant.parse(iso);
    }
}
