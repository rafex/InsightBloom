package dev.rafex.insightbloom.users.adapters.outbound.sqlite;

import dev.rafex.insightbloom.users.domain.model.VenueSeat;
import dev.rafex.insightbloom.users.domain.ports.VenueSeatRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SqliteVenueSeatRepository implements VenueSeatRepository {
    private final DatabaseManager db;

    public SqliteVenueSeatRepository(final DatabaseManager db) { this.db = db; }

    @Override
    public void save(final VenueSeat seat) {
        final String sql = """
            INSERT OR REPLACE INTO venue_seats (uuid, conference_uuid, label, x, y, created_at)
            VALUES (?, ?, ?, ?, ?, ?)
        """;
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, seat.getUuid());
            ps.setString(2, seat.getConferenceUuid());
            ps.setString(3, seat.getLabel());
            ps.setDouble(4, seat.getX());
            ps.setDouble(5, seat.getY());
            ps.setString(6, seat.getCreatedAt().toString());
            ps.executeUpdate();
        } catch (final SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void delete(final String uuid) {
        final String sql = "DELETE FROM venue_seats WHERE uuid = ?";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid);
            ps.executeUpdate();
        } catch (final SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Optional<VenueSeat> findByUuid(final String uuid) {
        final String sql = "SELECT * FROM venue_seats WHERE uuid = ?";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(map(rs));
            }
        } catch (final SQLException e) {
            throw new RuntimeException(e);
        }
        return Optional.empty();
    }

    @Override
    public List<VenueSeat> findByConference(final String conferenceUuid) {
        final String sql = "SELECT * FROM venue_seats WHERE conference_uuid = ? ORDER BY created_at ASC";
        final List<VenueSeat> result = new ArrayList<>();
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, conferenceUuid);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(map(rs));
            }
        } catch (final SQLException e) {
            throw new RuntimeException(e);
        }
        return result;
    }

    @Override
    public void deleteByConference(final String conferenceUuid) {
        final String sql = "DELETE FROM venue_seats WHERE conference_uuid = ?";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, conferenceUuid);
            ps.executeUpdate();
        } catch (final SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private VenueSeat map(final ResultSet rs) throws SQLException {
        return new VenueSeat(
                rs.getString("uuid"), rs.getString("conference_uuid"), rs.getString("label"),
                rs.getDouble("x"), rs.getDouble("y"), Instant.parse(rs.getString("created_at")));
    }
}
