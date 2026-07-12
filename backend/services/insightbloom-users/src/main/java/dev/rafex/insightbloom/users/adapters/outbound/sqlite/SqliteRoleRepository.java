package dev.rafex.insightbloom.users.adapters.outbound.sqlite;

import dev.rafex.insightbloom.users.domain.model.Permission;
import dev.rafex.insightbloom.users.domain.model.Role;
import dev.rafex.insightbloom.users.domain.model.RoleScope;
import dev.rafex.insightbloom.users.domain.ports.RoleRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class SqliteRoleRepository implements RoleRepository {
    private final DatabaseManager db;

    public SqliteRoleRepository(final DatabaseManager db) {
        this.db = db;
    }

    @Override
    public void save(final Role role) {
        final String sql = """
            INSERT OR REPLACE INTO roles
              (uuid, key, name, description, scope, permissions, active, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, role.getUuid());
            ps.setString(2, role.getKey());
            ps.setString(3, role.getName());
            ps.setString(4, role.getDescription());
            ps.setString(5, role.getScope().name());
            ps.setString(6, toCsv(role.getPermissions()));
            ps.setInt(7, role.isActive() ? 1 : 0);
            ps.setString(8, role.getCreatedAt().toString());
            ps.setString(9, role.getUpdatedAt().toString());
            ps.executeUpdate();
        } catch (final SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Optional<Role> findByUuid(final String uuid) {
        return query("SELECT * FROM roles WHERE uuid = ?", uuid);
    }

    @Override
    public Optional<Role> findByKey(final String key) {
        return query("SELECT * FROM roles WHERE key = ?", key);
    }

    @Override
    public boolean existsByKey(final String key) {
        final String sql = "SELECT 1 FROM roles WHERE key = ?";
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, key);
            return ps.executeQuery().next();
        } catch (final SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<Role> findAll() {
        return queryList("SELECT * FROM roles ORDER BY scope, name");
    }

    @Override
    public List<Role> findActive() {
        return queryList("SELECT * FROM roles WHERE active = 1 ORDER BY scope, name");
    }

    @Override
    public List<Role> findActiveByScope(final RoleScope scope) {
        final List<Role> list = new ArrayList<>();
        final String sql = "SELECT * FROM roles WHERE active = 1 AND scope = ? ORDER BY name";
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, scope.name());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(map(rs));
            }
        } catch (final SQLException e) {
            throw new RuntimeException(e);
        }
        return list;
    }

    private Optional<Role> query(final String sql, final String param) {
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(map(rs));
            }
        } catch (final SQLException e) {
            throw new RuntimeException(e);
        }
        return Optional.empty();
    }

    private List<Role> queryList(final String sql) {
        final List<Role> list = new ArrayList<>();
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(map(rs));
        } catch (final SQLException e) {
            throw new RuntimeException(e);
        }
        return list;
    }

    private static Role map(final ResultSet rs) throws SQLException {
        return new Role(
                rs.getString("uuid"), rs.getString("key"), rs.getString("name"), rs.getString("description"),
                RoleScope.valueOf(rs.getString("scope")), fromCsv(rs.getString("permissions")),
                rs.getInt("active") != 0, Instant.parse(rs.getString("created_at")),
                Instant.parse(rs.getString("updated_at")));
    }

    private static String toCsv(final Set<Permission> permissions) {
        return permissions.stream().map(Enum::name).collect(Collectors.joining(","));
    }

    private static Set<Permission> fromCsv(final String csv) {
        if (csv == null || csv.isBlank()) return Set.of();
        return Arrays.stream(csv.split(",")).map(Permission::valueOf).collect(Collectors.toSet());
    }
}
