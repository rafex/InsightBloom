package dev.rafex.insightbloom.users.adapters.outbound.sqlite;

import dev.rafex.insightbloom.users.domain.model.Ticket;
import dev.rafex.insightbloom.users.domain.model.TicketStatus;
import dev.rafex.insightbloom.users.domain.ports.TicketRepository;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SqliteTicketRepository implements TicketRepository {
    private final DatabaseManager db;

    public SqliteTicketRepository(final DatabaseManager db) { this.db = db; }

    @Override public void insert(final Ticket t) {
        final String sql = "INSERT INTO tickets (uuid, conference_uuid, ticket_code, issued_by_user_uuid, recipient_email, seat_uuid, status, claimed_by_user_uuid, issued_at, claimed_at, checked_in_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, t.getUuid()); ps.setString(2, t.getConferenceUuid()); ps.setString(3, t.getTicketCode());
            ps.setString(4, t.getIssuedByUserUuid()); ps.setString(5, t.getRecipientEmail()); ps.setString(6, t.getSeatUuid());
            ps.setString(7, t.getStatus().name()); ps.setString(8, t.getClaimedByUserUuid());
            ps.setString(9, t.getIssuedAt().toString()); ps.setString(10, null); ps.setString(11, null);
            ps.executeUpdate();
        } catch (SQLException e) { throw new RuntimeException(e.getMessage(), e); }
    }

    @Override public Optional<Ticket> findByUuid(final String uuid) { return query("SELECT * FROM tickets WHERE uuid = ?", uuid); }

    @Override public Optional<Ticket> findByCode(final String conferenceUuid, final String code) {
        return query("SELECT * FROM tickets WHERE conference_uuid = ? AND ticket_code = ?", conferenceUuid, code);
    }

    @Override public Optional<Ticket> findByConferenceAndUser(final String conferenceUuid, final String userUuid) {
        return query("SELECT * FROM tickets WHERE conference_uuid = ? AND claimed_by_user_uuid = ? AND status NOT IN ('REVOKED', 'EXPIRED') ORDER BY issued_at DESC LIMIT 1", conferenceUuid, userUuid);
    }

    @Override public List<Ticket> findByConference(final String conferenceUuid) {
        final List<Ticket> result = new ArrayList<>();
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement("SELECT * FROM tickets WHERE conference_uuid = ? ORDER BY issued_at DESC")) {
            ps.setString(1, conferenceUuid);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) result.add(map(rs)); }
        } catch (SQLException e) { throw new RuntimeException(e); }
        return result;
    }

    @Override public boolean claim(final String uuid, final String userUuid, final String claimedAt) {
        final String sql = "UPDATE tickets SET claimed_by_user_uuid = ?, claimed_at = ?, status = 'CLAIMED' WHERE uuid = ? AND status = 'ISSUED' AND claimed_by_user_uuid IS NULL";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, userUuid); ps.setString(2, claimedAt); ps.setString(3, uuid); return ps.executeUpdate() == 1;
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    @Override public boolean checkIn(final String uuid, final String checkedInAt) {
        final String sql = "UPDATE tickets SET status = 'CHECKED_IN', checked_in_at = ? WHERE uuid = ? AND status IN ('CLAIMED', 'ISSUED')";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, checkedInAt); ps.setString(2, uuid); return ps.executeUpdate() == 1;
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    @Override public boolean revoke(final String uuid) {
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(
                "UPDATE tickets SET status = 'REVOKED' WHERE uuid = ? AND status IN ('ISSUED', 'CLAIMED')")) {
            ps.setString(1, uuid); return ps.executeUpdate() == 1;
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    @Override public void expireByConference(final String conferenceUuid, final String expiredAt) {
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(
                "UPDATE tickets SET status = 'EXPIRED' WHERE conference_uuid = ? AND status IN ('ISSUED', 'CLAIMED')")) {
            ps.setString(1, conferenceUuid); ps.executeUpdate();
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    private Optional<Ticket> query(final String sql, final String... args) {
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) ps.setString(i + 1, args[i]);
            try (ResultSet rs = ps.executeQuery()) { if (rs.next()) return Optional.of(map(rs)); }
        } catch (SQLException e) { throw new RuntimeException(e); }
        return Optional.empty();
    }

    private Ticket map(final ResultSet rs) throws SQLException {
        return new Ticket(rs.getString("uuid"), rs.getString("conference_uuid"), rs.getString("ticket_code"),
                rs.getString("issued_by_user_uuid"), rs.getString("recipient_email"), rs.getString("seat_uuid"),
                TicketStatus.valueOf(rs.getString("status")), rs.getString("claimed_by_user_uuid"),
                Instant.parse(rs.getString("issued_at")), parse(rs.getString("claimed_at")), parse(rs.getString("checked_in_at")));
    }

    private static Instant parse(final String value) { return value == null ? null : Instant.parse(value); }
}
