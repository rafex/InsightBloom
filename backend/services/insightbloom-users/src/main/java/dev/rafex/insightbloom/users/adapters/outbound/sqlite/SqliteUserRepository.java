package dev.rafex.insightbloom.users.adapters.outbound.sqlite;

import dev.rafex.insightbloom.users.domain.model.SocialLink;
import dev.rafex.insightbloom.users.domain.model.User;
import dev.rafex.insightbloom.users.domain.model.UserRole;
import dev.rafex.insightbloom.users.domain.model.UserStatus;
import dev.rafex.insightbloom.users.domain.ports.UserRepository;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class SqliteUserRepository implements UserRepository {
    private static final String LINK_SEP = ";;";
    private static final String FIELD_SEP = "::";

    private final DatabaseManager db;

    public SqliteUserRepository(DatabaseManager db) {
        this.db = db;
    }

    @Override
    public void save(User user) {
        String sql = """
            INSERT OR REPLACE INTO users
                (uuid, username, display_name, email, phone, social_links, email_verified, phone_verified,
                 role, status, password_hash, created_at, updated_at, first_name, last_name, last_login_at,
                 registration_device_fingerprint)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, user.getUuid());
            ps.setString(2, user.getUsername());
            ps.setString(3, user.getDisplayName());
            ps.setString(4, user.getEmail());
            ps.setString(5, user.getPhone());
            ps.setString(6, encodeLinks(user.getSocialLinks()));
            ps.setInt(7, user.isEmailVerified() ? 1 : 0);
            ps.setInt(8, user.isPhoneVerified() ? 1 : 0);
            ps.setString(9, encodeRoles(user.getRoles()));
            ps.setString(10, user.getStatus().name());
            ps.setString(11, user.getPasswordHash());
            ps.setString(12, user.getCreatedAt().toString());
            ps.setString(13, user.getUpdatedAt().toString());
            ps.setString(14, user.getFirstName());
            ps.setString(15, user.getLastName());
            ps.setString(16, user.getLastLoginAt() != null ? user.getLastLoginAt().toString() : null);
            ps.setString(17, user.getRegistrationDeviceFingerprint());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Optional<User> findByUuid(String uuid) {
        return query("SELECT * FROM users WHERE uuid = ?", uuid);
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return query("SELECT * FROM users WHERE username = ?", username);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return query("SELECT * FROM users WHERE email = ?", email);
    }

    @Override
    public Optional<User> findByPhone(String phone) {
        return query("SELECT * FROM users WHERE phone = ?", phone);
    }

    @Override
    public List<User> findAll(int page, int pageSize, UserStatus status, UserRole role, String sort) {
        final List<User> users = new ArrayList<>();
        final StringBuilder sql = new StringBuilder("SELECT * FROM users WHERE 1=1");
        final List<Object> params = new ArrayList<>();
        appendFilters(sql, params, status, role);
        sql.append("username".equals(sort) ? " ORDER BY username COLLATE NOCASE ASC" : " ORDER BY created_at DESC");
        sql.append(" LIMIT ? OFFSET ?");
        params.add(pageSize);
        params.add((page - 1) * pageSize);
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) ps.setObject(i + 1, params.get(i));
            ResultSet rs = ps.executeQuery();
            while (rs.next()) users.add(map(rs));
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return users;
    }

    @Override
    public long countAll(UserStatus status, UserRole role) {
        final StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM users WHERE 1=1");
        final List<Object> params = new ArrayList<>();
        appendFilters(sql, params, status, role);
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) ps.setObject(i + 1, params.get(i));
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getLong(1);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return 0;
    }

    @Override
    public long countByRegistrationFingerprintSince(final String deviceFingerprint, final Instant since) {
        final String sql = "SELECT COUNT(*) FROM users WHERE registration_device_fingerprint = ? AND created_at > ?";
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, deviceFingerprint);
            ps.setString(2, since.toString());
            final ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getLong(1);
        } catch (final SQLException e) {
            throw new RuntimeException(e);
        }
        return 0;
    }

    private void appendFilters(StringBuilder sql, List<Object> params, UserStatus status, UserRole role) {
        if (status != null) {
            sql.append(" AND status = ?");
            params.add(status.name());
        }
        if (role != null) {
            sql.append(" AND role LIKE ?");
            params.add("%" + role.name() + "%");
        }
    }

    private Optional<User> query(String sql, String param) {
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, param);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return Optional.of(map(rs));
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return Optional.empty();
    }

    private User map(ResultSet rs) throws SQLException {
        User user = new User(
            rs.getString("id"), rs.getString("uuid"), rs.getString("username"),
            rs.getString("display_name"), rs.getString("email"), rs.getString("phone"),
            decodeLinks(rs.getString("social_links")),
            rs.getInt("email_verified") == 1, rs.getInt("phone_verified") == 1,
            decodeRoles(rs.getString("role")), UserStatus.valueOf(rs.getString("status")),
            rs.getString("password_hash"),
            parseInstant(rs.getString("created_at")), parseInstant(rs.getString("updated_at"))
        );
        user.setFirstName(rs.getString("first_name"));
        user.setLastName(rs.getString("last_name"));
        final String lastLoginAt = rs.getString("last_login_at");
        if (lastLoginAt != null) user.setLastLoginAt(parseInstant(lastLoginAt));
        user.setRegistrationDeviceFingerprint(rs.getString("registration_device_fingerprint"));
        return user;
    }

    private String encodeRoles(Set<UserRole> roles) {
        final List<String> names = new ArrayList<>();
        for (final UserRole r : roles) names.add(r.name());
        return String.join(",", names);
    }

    private Set<UserRole> decodeRoles(String raw) {
        final Set<UserRole> roles = new LinkedHashSet<>();
        if (raw == null || raw.isBlank()) return roles;
        for (final String part : raw.split(",")) {
            final String trimmed = part.trim();
            if (!trimmed.isEmpty()) roles.add(UserRole.valueOf(trimmed));
        }
        return roles;
    }

    private String encodeLinks(List<SocialLink> links) {
        if (links == null || links.isEmpty()) return null;
        final List<String> parts = new ArrayList<>();
        for (final SocialLink l : links) parts.add(l.platform() + FIELD_SEP + l.url());
        return String.join(LINK_SEP, parts);
    }

    private List<SocialLink> decodeLinks(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        final List<SocialLink> links = new ArrayList<>();
        for (final String part : raw.split(LINK_SEP)) {
            final String[] kv = part.split(FIELD_SEP, 2);
            if (kv.length == 2) links.add(new SocialLink(kv[0], kv[1]));
        }
        return links;
    }

    private static Instant parseInstant(String s) {
        if (s == null) return Instant.now();
        String iso = s.contains("T") ? s : s.replace(" ", "T") + "Z";
        return Instant.parse(iso);
    }
}
