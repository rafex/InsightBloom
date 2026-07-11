package dev.rafex.insightbloom.users.adapters.outbound.sqlite;

import dev.rafex.insightbloom.users.domain.model.Reservation;
import dev.rafex.insightbloom.users.domain.model.ReservationStatus;
import dev.rafex.insightbloom.users.domain.ports.ReservationRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SqliteReservationRepository implements ReservationRepository {
    private final DatabaseManager db;

    public SqliteReservationRepository(final DatabaseManager db) { this.db = db; }

    @Override
    public void save(final Reservation reservation) {
        final String sql = """
            INSERT OR REPLACE INTO reservations
                (uuid, conference_uuid, user_uuid, seat_uuid, ticket_code, status, created_at, checked_in_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """;
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, reservation.getUuid());
            ps.setString(2, reservation.getConferenceUuid());
            ps.setString(3, reservation.getUserUuid());
            ps.setString(4, reservation.getSeatUuid());
            ps.setString(5, reservation.getTicketCode());
            ps.setString(6, reservation.getStatus().name());
            ps.setString(7, reservation.getCreatedAt().toString());
            ps.setString(8, reservation.getCheckedInAt() != null ? reservation.getCheckedInAt().toString() : null);
            ps.executeUpdate();
        } catch (final SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void insertNew(final Reservation reservation) {
        final String sql = """
            INSERT INTO reservations
                (uuid, conference_uuid, user_uuid, seat_uuid, ticket_code, status, created_at, checked_in_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """;
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, reservation.getUuid());
            ps.setString(2, reservation.getConferenceUuid());
            ps.setString(3, reservation.getUserUuid());
            ps.setString(4, reservation.getSeatUuid());
            ps.setString(5, reservation.getTicketCode());
            ps.setString(6, reservation.getStatus().name());
            ps.setString(7, reservation.getCreatedAt().toString());
            ps.setString(8, reservation.getCheckedInAt() != null ? reservation.getCheckedInAt().toString() : null);
            ps.executeUpdate();
        } catch (final SQLException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    @Override
    public void delete(final String uuid) {
        final String sql = "DELETE FROM reservations WHERE uuid = ?";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid);
            ps.executeUpdate();
        } catch (final SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Optional<Reservation> findByUuid(final String uuid) {
        return query("SELECT * FROM reservations WHERE uuid = ?", uuid);
    }

    @Override
    public Optional<Reservation> findByTicketCode(final String conferenceUuid, final String ticketCode) {
        final String sql = "SELECT * FROM reservations WHERE conference_uuid = ? AND ticket_code = ?";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, conferenceUuid);
            ps.setString(2, ticketCode);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(map(rs));
            }
        } catch (final SQLException e) {
            throw new RuntimeException(e);
        }
        return Optional.empty();
    }

    @Override
    public Optional<Reservation> findByConferenceAndUser(final String conferenceUuid, final String userUuid) {
        final String sql = "SELECT * FROM reservations WHERE conference_uuid = ? AND user_uuid = ?";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, conferenceUuid);
            ps.setString(2, userUuid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(map(rs));
            }
        } catch (final SQLException e) {
            throw new RuntimeException(e);
        }
        return Optional.empty();
    }

    @Override
    public List<Reservation> findByConference(final String conferenceUuid) {
        final String sql = "SELECT * FROM reservations WHERE conference_uuid = ? ORDER BY created_at DESC";
        final List<Reservation> result = new ArrayList<>();
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

    private Optional<Reservation> query(final String sql, final String uuid) {
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

    private Reservation map(final ResultSet rs) throws SQLException {
        final String checkedInAt = rs.getString("checked_in_at");
        return new Reservation(
                rs.getString("uuid"), rs.getString("conference_uuid"), rs.getString("user_uuid"),
                rs.getString("seat_uuid"), rs.getString("ticket_code"),
                ReservationStatus.valueOf(rs.getString("status")),
                Instant.parse(rs.getString("created_at")),
                checkedInAt != null ? Instant.parse(checkedInAt) : null
        );
    }
}
