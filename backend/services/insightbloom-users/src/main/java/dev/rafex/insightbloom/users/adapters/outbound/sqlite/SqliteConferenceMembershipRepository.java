package dev.rafex.insightbloom.users.adapters.outbound.sqlite;

import dev.rafex.insightbloom.users.domain.model.ConferenceMembership;
import dev.rafex.insightbloom.users.domain.ports.ConferenceMembershipRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class SqliteConferenceMembershipRepository implements ConferenceMembershipRepository {
    private final DatabaseManager db;

    public SqliteConferenceMembershipRepository(final DatabaseManager db) { this.db = db; }

    @Override
    public void recordJoin(final ConferenceMembership membership) {
        final String sql = """
            INSERT INTO conference_memberships
                (uuid, user_uuid, conference_uuid, conference_name_snapshot,
                 conference_friendly_id_snapshot, joined_at)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT(user_uuid, conference_uuid) DO UPDATE SET
                conference_name_snapshot = excluded.conference_name_snapshot,
                conference_friendly_id_snapshot = excluded.conference_friendly_id_snapshot,
                joined_at = excluded.joined_at
        """;
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, membership.getUuid());
            ps.setString(2, membership.getUserUuid());
            ps.setString(3, membership.getConferenceUuid());
            ps.setString(4, membership.getConferenceNameSnapshot());
            ps.setString(5, membership.getConferenceFriendlyIdSnapshot());
            ps.setString(6, membership.getJoinedAt().toString());
            ps.executeUpdate();
        } catch (final SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean exists(final String userUuid, final String conferenceUuid) {
        final String sql = "SELECT 1 FROM conference_memberships WHERE user_uuid = ? AND conference_uuid = ?";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, userUuid);
            ps.setString(2, conferenceUuid);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (final SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<ConferenceMembership> findByUser(final String userUuid) {
        final String sql = "SELECT * FROM conference_memberships WHERE user_uuid = ? ORDER BY joined_at DESC";
        final List<ConferenceMembership> result = new ArrayList<>();
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, userUuid);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new ConferenceMembership(
                            rs.getString("uuid"), rs.getString("user_uuid"), rs.getString("conference_uuid"),
                            rs.getString("conference_name_snapshot"), rs.getString("conference_friendly_id_snapshot"),
                            Instant.parse(rs.getString("joined_at"))));
                }
            }
        } catch (final SQLException e) {
            throw new RuntimeException(e);
        }
        return result;
    }
}
