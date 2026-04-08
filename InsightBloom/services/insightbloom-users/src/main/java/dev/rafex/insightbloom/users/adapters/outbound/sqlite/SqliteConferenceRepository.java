package dev.rafex.insightbloom.users.adapters.outbound.sqlite;

import dev.rafex.insightbloom.users.domain.model.Conference;
import dev.rafex.insightbloom.users.domain.model.ConferenceStatus;
import dev.rafex.insightbloom.users.domain.ports.ConferenceRepository;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SqliteConferenceRepository implements ConferenceRepository {
    private final DatabaseManager db;

    public SqliteConferenceRepository(DatabaseManager db) {
        this.db = db;
    }

    @Override
    public void save(Conference conference) {
        String sql = """
            INSERT OR REPLACE INTO conferences
              (uuid, friendly_id, name, created_by_user_uuid, status, created_at, updated_at, expires_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """;
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, conference.getUuid());
            ps.setString(2, conference.getFriendlyId());
            ps.setString(3, conference.getName());
            ps.setString(4, conference.getCreatedByUserUuid());
            ps.setString(5, conference.getStatus().name());
            ps.setString(6, conference.getCreatedAt().toString());
            ps.setString(7, conference.getUpdatedAt().toString());
            ps.setString(8, conference.getExpiresAt() != null ? conference.getExpiresAt().toString() : null);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void delete(String uuid) {
        String sql = "DELETE FROM conferences WHERE uuid = ?";
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Optional<Conference> findByUuid(String uuid) {
        return query("SELECT * FROM conferences WHERE uuid = ?", uuid);
    }

    @Override
    public Optional<Conference> findByFriendlyId(String friendlyId) {
        return query("SELECT * FROM conferences WHERE friendly_id = ?", friendlyId);
    }

    @Override
    public boolean existsByFriendlyId(String friendlyId) {
        String sql = "SELECT 1 FROM conferences WHERE friendly_id = ?";
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, friendlyId);
            return ps.executeQuery().next();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<Conference> findByUser(String userUuid) {
        List<Conference> list = new ArrayList<>();
        String sql = "SELECT * FROM conferences WHERE created_by_user_uuid = ? ORDER BY created_at DESC";
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userUuid);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(map(rs));
        } catch (SQLException e) { throw new RuntimeException(e); }
        return list;
    }

    private Conference map(ResultSet rs) throws SQLException {
        return new Conference(
            rs.getString("uuid"), rs.getString("friendly_id"), rs.getString("name"),
            rs.getString("created_by_user_uuid"),
            ConferenceStatus.valueOf(rs.getString("status")),
            parseInstant(rs.getString("created_at")), parseInstant(rs.getString("updated_at")),
            parseInstantNullable(rs.getString("expires_at"))
        );
    }

    private Optional<Conference> query(String sql, String param) {
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, param);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return Optional.of(map(rs));
        } catch (SQLException e) { throw new RuntimeException(e); }
        return Optional.empty();
    }

    private static Instant parseInstant(String s) {
        if (s == null) return Instant.now();
        String iso = s.contains("T") ? s : s.replace(" ", "T") + "Z";
        return Instant.parse(iso);
    }

    private static Instant parseInstantNullable(String s) {
        if (s == null || s.isBlank()) return null;
        String iso = s.contains("T") ? s : s.replace(" ", "T") + "Z";
        return Instant.parse(iso);
    }
}
