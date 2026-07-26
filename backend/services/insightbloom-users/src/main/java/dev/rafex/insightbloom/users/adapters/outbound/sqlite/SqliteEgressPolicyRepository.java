package dev.rafex.insightbloom.users.adapters.outbound.sqlite;

import dev.rafex.insightbloom.users.domain.model.EgressPolicy;
import dev.rafex.insightbloom.users.domain.ports.EgressPolicyRepository;

import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;

public class SqliteEgressPolicyRepository implements EgressPolicyRepository {
    private final DatabaseManager database;

    public SqliteEgressPolicyRepository(final DatabaseManager database) {
        this.database = database;
    }

    @Override
    public Optional<EgressPolicy> findByConference(final String conferenceUuid) {
        try (var c = database.getConnection(); var ps = c.prepareStatement("""
                SELECT conference_uuid, allowed_hosts, blocked_hosts, updated_at
                FROM egress_policies WHERE conference_uuid = ?""")) {
            ps.setString(1, conferenceUuid);
            try (var rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(read(rs));
            }
        } catch (final SQLException e) {
            throw new RuntimeException("Failed to read egress policy", e);
        }
    }

    @Override
    public EgressPolicy save(final EgressPolicy policy) {
        try (var c = database.getConnection(); var ps = c.prepareStatement("""
                INSERT INTO egress_policies (conference_uuid, allowed_hosts, blocked_hosts, updated_at)
                VALUES (?, ?, ?, ?)
                ON CONFLICT(conference_uuid) DO UPDATE SET
                    allowed_hosts = excluded.allowed_hosts,
                    blocked_hosts = excluded.blocked_hosts,
                    updated_at = excluded.updated_at""")) {
            ps.setString(1, policy.conferenceUuid());
            ps.setString(2, policy.allowedHosts());
            ps.setString(3, policy.blockedHosts());
            ps.setString(4, policy.updatedAt().toString());
            ps.executeUpdate();
            return policy;
        } catch (final SQLException e) {
            throw new RuntimeException("Failed to save egress policy", e);
        }
    }

    @Override
    public void deleteByConference(final String conferenceUuid) {
        try (var c = database.getConnection(); var ps = c.prepareStatement(
                "DELETE FROM egress_policies WHERE conference_uuid = ?")) {
            ps.setString(1, conferenceUuid);
            ps.executeUpdate();
        } catch (final SQLException e) {
            throw new RuntimeException("Failed to delete egress policy", e);
        }
    }

    private EgressPolicy read(final java.sql.ResultSet rs) throws SQLException {
        return new EgressPolicy(
                rs.getString("conference_uuid"),
                rs.getString("allowed_hosts"),
                rs.getString("blocked_hosts"),
                Instant.parse(rs.getString("updated_at")));
    }
}
