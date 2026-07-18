package dev.rafex.insightbloom.users.adapters.outbound.sqlite;

import dev.rafex.insightbloom.users.domain.model.Sandbox;
import dev.rafex.insightbloom.users.domain.ports.SandboxRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SqliteSandboxRepository implements SandboxRepository {
    private final DatabaseManager databaseManager;

    public SqliteSandboxRepository(final DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    @Override
    public void save(final Sandbox sandbox) {
        try (final Connection conn = databaseManager.getConnection();
             final PreparedStatement stmt = conn.prepareStatement("""
                INSERT INTO sandbox_assignments (
                    uuid, conference_uuid, sandbox_slot, seat_index, user_uuid, assigned_at,
                    created_at, expires_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            stmt.setString(1, sandbox.getUuid());
            stmt.setString(2, sandbox.getConferenceUuid());
            stmt.setInt(3, sandbox.getSandboxSlot());
            stmt.setInt(4, sandbox.getSeatIndex());
            stmt.setString(5, sandbox.getUserUuid());
            stmt.setString(6, sandbox.getAssignedAt() != null ? sandbox.getAssignedAt().toString() : null);
            stmt.setString(7, sandbox.getCreatedAt().toString());
            stmt.setString(8, sandbox.getExpiresAt().toString());
            stmt.executeUpdate();
        } catch (final SQLException e) {
            throw new RuntimeException("Failed to save sandbox", e);
        }
    }

    @Override
    public Optional<Sandbox> findByUuid(final String uuid) {
        try (final Connection conn = databaseManager.getConnection();
             final PreparedStatement stmt = conn.prepareStatement(
                "SELECT uuid, conference_uuid, sandbox_slot, seat_index, user_uuid, assigned_at, created_at, expires_at " +
                "FROM sandbox_assignments WHERE uuid = ?")) {
            stmt.setString(1, uuid);
            try (final ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRowToSandbox(rs));
                }
            }
        } catch (final SQLException e) {
            throw new RuntimeException("Failed to find sandbox by uuid", e);
        }
        return Optional.empty();
    }

    @Override
    public List<Sandbox> findByConferenceUuid(final String conferenceUuid) {
        final List<Sandbox> sandboxes = new ArrayList<>();
        try (final Connection conn = databaseManager.getConnection();
             final PreparedStatement stmt = conn.prepareStatement(
                "SELECT uuid, conference_uuid, sandbox_slot, seat_index, user_uuid, assigned_at, created_at, expires_at " +
                "FROM sandbox_assignments WHERE conference_uuid = ? ORDER BY sandbox_slot ASC")) {
            stmt.setString(1, conferenceUuid);
            try (final ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    sandboxes.add(mapRowToSandbox(rs));
                }
            }
        } catch (final SQLException e) {
            throw new RuntimeException("Failed to find sandboxes by conference", e);
        }
        return sandboxes;
    }

    @Override
    public List<Sandbox> findExpired(final Instant now) {
        final List<Sandbox> sandboxes = new ArrayList<>();
        try (final Connection conn = databaseManager.getConnection();
             final PreparedStatement stmt = conn.prepareStatement(
                "SELECT uuid, conference_uuid, sandbox_slot, seat_index, user_uuid, assigned_at, created_at, expires_at " +
                "FROM sandbox_assignments WHERE expires_at < ?")) {
            stmt.setString(1, now.toString());
            try (final ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    sandboxes.add(mapRowToSandbox(rs));
                }
            }
        } catch (final SQLException e) {
            throw new RuntimeException("Failed to find expired sandboxes", e);
        }
        return sandboxes;
    }

    @Override
    public Optional<Sandbox> findByConferenceAndUser(final String conferenceUuid, final String userUuid) {
        try (final Connection conn = databaseManager.getConnection();
             final PreparedStatement stmt = conn.prepareStatement(
                "SELECT uuid, conference_uuid, sandbox_slot, seat_index, user_uuid, assigned_at, created_at, expires_at " +
                "FROM sandbox_assignments WHERE conference_uuid = ? AND user_uuid = ?")) {
            stmt.setString(1, conferenceUuid);
            stmt.setString(2, userUuid);
            try (final ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRowToSandbox(rs));
                }
            }
        } catch (final SQLException e) {
            throw new RuntimeException("Failed to find sandbox by conference and user", e);
        }
        return Optional.empty();
    }

    @Override
    public Optional<Sandbox> findUnassigned(final String conferenceUuid) {
        try (final Connection conn = databaseManager.getConnection();
             final PreparedStatement stmt = conn.prepareStatement(
                "SELECT uuid, conference_uuid, sandbox_slot, seat_index, user_uuid, assigned_at, created_at, expires_at " +
                "FROM sandbox_assignments WHERE conference_uuid = ? AND user_uuid IS NULL " +
                "ORDER BY sandbox_slot ASC LIMIT 1")) {
            stmt.setString(1, conferenceUuid);
            try (final ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRowToSandbox(rs));
                }
            }
        } catch (final SQLException e) {
            throw new RuntimeException("Failed to find unassigned sandbox", e);
        }
        return Optional.empty();
    }

    @Override
    public boolean claim(final String uuid, final String userUuid, final Instant assignedAt) {
        try (final Connection conn = databaseManager.getConnection();
             final PreparedStatement stmt = conn.prepareStatement(
                "UPDATE sandbox_assignments SET user_uuid = ?, assigned_at = ? " +
                "WHERE uuid = ? AND user_uuid IS NULL")) {
            stmt.setString(1, userUuid);
            stmt.setString(2, assignedAt.toString());
            stmt.setString(3, uuid);
            return stmt.executeUpdate() == 1;
        } catch (final SQLException e) {
            throw new RuntimeException("Failed to claim sandbox", e);
        }
    }

    @Override
    public void deleteByConferenceUuid(final String conferenceUuid) {
        try (final Connection conn = databaseManager.getConnection();
             final PreparedStatement stmt = conn.prepareStatement(
                "DELETE FROM sandbox_assignments WHERE conference_uuid = ?")) {
            stmt.setString(1, conferenceUuid);
            stmt.executeUpdate();
        } catch (final SQLException e) {
            throw new RuntimeException("Failed to delete sandboxes by conference", e);
        }
    }

    @Override
    public int deleteExpired(final Instant now) {
        try (final Connection conn = databaseManager.getConnection();
             final PreparedStatement stmt = conn.prepareStatement(
                "DELETE FROM sandbox_assignments WHERE expires_at < ?")) {
            stmt.setString(1, now.toString());
            return stmt.executeUpdate();
        } catch (final SQLException e) {
            throw new RuntimeException("Failed to delete expired sandboxes", e);
        }
    }

    private Sandbox mapRowToSandbox(final ResultSet rs) throws SQLException {
        final String assignedAtStr = rs.getString("assigned_at");
        final Instant assignedAt = assignedAtStr != null ? Instant.parse(assignedAtStr) : null;
        return new Sandbox(
            rs.getString("uuid"),
            rs.getString("conference_uuid"),
            rs.getInt("sandbox_slot"),
            rs.getInt("seat_index"),
            rs.getString("user_uuid"),
            assignedAt,
            Instant.parse(rs.getString("created_at")),
            Instant.parse(rs.getString("expires_at"))
        );
    }
}
