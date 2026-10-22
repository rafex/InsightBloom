package dev.rafex.insightbloom.users.adapters.outbound.sqlite;

import dev.rafex.insightbloom.users.domain.model.VenueMapConfig;
import dev.rafex.insightbloom.users.domain.ports.VenueMapConfigRepository;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public class SqliteVenueMapConfigRepository implements VenueMapConfigRepository {
    private final DatabaseManager db;

    public SqliteVenueMapConfigRepository(final DatabaseManager db) { this.db = db; }

    @Override
    public Optional<VenueMapConfig> findByConference(final String conferenceUuid) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT engine, layout_json FROM conference_venue_map_configs WHERE conference_uuid = ?")) {
            ps.setString(1, conferenceUuid);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(new VenueMapConfig(conferenceUuid, rs.getString("engine"), rs.getString("layout_json"))) : Optional.empty();
            }
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    @Override
    public void save(final VenueMapConfig config) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("INSERT INTO conference_venue_map_configs(conference_uuid,engine,layout_json) VALUES(?,?,?) ON CONFLICT(conference_uuid) DO UPDATE SET engine=excluded.engine, layout_json=excluded.layout_json")) {
            ps.setString(1, config.conferenceUuid()); ps.setString(2, config.engine()); ps.setString(3, config.layoutJson()); ps.executeUpdate();
        } catch (SQLException e) { throw new RuntimeException(e); }
    }
}
