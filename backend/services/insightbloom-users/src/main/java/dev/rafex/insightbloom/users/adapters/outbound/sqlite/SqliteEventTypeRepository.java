package dev.rafex.insightbloom.users.adapters.outbound.sqlite;

import dev.rafex.insightbloom.users.domain.model.EventCapability;
import dev.rafex.insightbloom.users.domain.model.EventType;
import dev.rafex.insightbloom.users.domain.ports.EventTypeRepository;

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

public class SqliteEventTypeRepository implements EventTypeRepository {
    private final DatabaseManager db;

    public SqliteEventTypeRepository(final DatabaseManager db) {
        this.db = db;
    }

    @Override
    public void save(final EventType eventType) {
        final String sql = """
            INSERT OR REPLACE INTO event_types
              (uuid, key, name, description, capabilities, active, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """;
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, eventType.getUuid());
            ps.setString(2, eventType.getKey());
            ps.setString(3, eventType.getName());
            ps.setString(4, eventType.getDescription());
            ps.setString(5, toCsv(eventType.getCapabilities()));
            ps.setInt(6, eventType.isActive() ? 1 : 0);
            ps.setString(7, eventType.getCreatedAt().toString());
            ps.setString(8, eventType.getUpdatedAt().toString());
            ps.executeUpdate();
        } catch (final SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Optional<EventType> findByUuid(final String uuid) {
        return query("SELECT * FROM event_types WHERE uuid = ?", uuid);
    }

    @Override
    public Optional<EventType> findByKey(final String key) {
        return query("SELECT * FROM event_types WHERE key = ?", key);
    }

    @Override
    public boolean existsByKey(final String key) {
        final String sql = "SELECT 1 FROM event_types WHERE key = ?";
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, key);
            return ps.executeQuery().next();
        } catch (final SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<EventType> findAll() {
        return queryList("SELECT * FROM event_types ORDER BY name");
    }

    @Override
    public List<EventType> findActive() {
        return queryList("SELECT * FROM event_types WHERE active = 1 ORDER BY name");
    }

    private Optional<EventType> query(final String sql, final String param) {
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

    private List<EventType> queryList(final String sql) {
        final List<EventType> list = new ArrayList<>();
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(map(rs));
        } catch (final SQLException e) {
            throw new RuntimeException(e);
        }
        return list;
    }

    private static EventType map(final ResultSet rs) throws SQLException {
        return new EventType(
                rs.getString("uuid"), rs.getString("key"), rs.getString("name"), rs.getString("description"),
                fromCsv(rs.getString("capabilities")), rs.getInt("active") != 0,
                Instant.parse(rs.getString("created_at")), Instant.parse(rs.getString("updated_at")));
    }

    private static String toCsv(final Set<EventCapability> capabilities) {
        return capabilities.stream().map(Enum::name).collect(Collectors.joining(","));
    }

    private static Set<EventCapability> fromCsv(final String csv) {
        if (csv == null || csv.isBlank()) return Set.of();
        return Arrays.stream(csv.split(",")).map(EventCapability::valueOf).collect(Collectors.toSet());
    }
}
