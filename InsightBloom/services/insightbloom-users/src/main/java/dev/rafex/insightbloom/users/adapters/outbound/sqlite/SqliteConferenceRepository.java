package dev.rafex.insightbloom.users.adapters.outbound.sqlite;

import dev.rafex.insightbloom.users.domain.model.Conference;
import dev.rafex.insightbloom.users.domain.model.ConferenceStatus;
import dev.rafex.insightbloom.users.domain.ports.ConferenceRepository;

import java.sql.*;
import java.time.Instant;
import java.util.Optional;

public class SqliteConferenceRepository implements ConferenceRepository {
    private final DatabaseManager db;

    public SqliteConferenceRepository(DatabaseManager db) {
        this.db = db;
    }

    @Override
    public void save(Conference conference) {
        String sql = """
            INSERT OR REPLACE INTO conferences (uuid, friendly_id, name, created_by_user_uuid, status, created_at, updated_at, latitude, longitude)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, conference.getUuid());
            ps.setString(2, conference.getFriendlyId());
            ps.setString(3, conference.getName());
            ps.setString(4, conference.getCreatedByUserUuid());
            ps.setString(5, conference.getStatus().name());
            ps.setString(6, conference.getCreatedAt().toString());
            ps.setString(7, conference.getUpdatedAt().toString());
            if (conference.getLatitude() != null) ps.setDouble(8, conference.getLatitude());
            else ps.setNull(8, Types.REAL);
            if (conference.getLongitude() != null) ps.setDouble(9, conference.getLongitude());
            else ps.setNull(9, Types.REAL);
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

    private Optional<Conference> query(String sql, String param) {
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, param);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                double lat = rs.getDouble("latitude");
                Double latitude = rs.wasNull() ? null : lat;
                double lng = rs.getDouble("longitude");
                Double longitude = rs.wasNull() ? null : lng;
                return Optional.of(new Conference(
                    rs.getString("uuid"), rs.getString("friendly_id"), rs.getString("name"),
                    rs.getString("created_by_user_uuid"),
                    ConferenceStatus.valueOf(rs.getString("status")),
                    Instant.parse(rs.getString("created_at")), Instant.parse(rs.getString("updated_at")),
                    latitude, longitude
                ));
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return Optional.empty();
    }
}
