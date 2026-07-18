package dev.rafex.insightbloom.users.adapters.outbound.sqlite;

import dev.rafex.insightbloom.users.domain.model.SandboxIncident;
import dev.rafex.insightbloom.users.domain.ports.SandboxIncidentRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class SqliteSandboxIncidentRepository implements SandboxIncidentRepository {
    private final DatabaseManager databaseManager;

    public SqliteSandboxIncidentRepository(final DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    @Override
    public void save(final SandboxIncident incident) {
        try (final Connection conn = databaseManager.getConnection();
             final PreparedStatement stmt = conn.prepareStatement("""
                INSERT INTO sandbox_incidents (
                    uuid, conference_uuid, pod_name, seat_index, user_uuid, type, detail, occurred_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            stmt.setString(1, incident.getUuid());
            stmt.setString(2, incident.getConferenceUuid());
            stmt.setString(3, incident.getPodName());
            stmt.setInt(4, incident.getSeatIndex());
            stmt.setString(5, incident.getUserUuid());
            stmt.setString(6, incident.getType());
            stmt.setString(7, incident.getDetail());
            stmt.setString(8, incident.getOccurredAt().toString());
            stmt.executeUpdate();
        } catch (final SQLException e) {
            throw new RuntimeException("Failed to save sandbox incident", e);
        }
    }

    @Override
    public List<SandboxIncident> findByConferenceUuid(final String conferenceUuid) {
        final List<SandboxIncident> incidents = new ArrayList<>();
        try (final Connection conn = databaseManager.getConnection();
             final PreparedStatement stmt = conn.prepareStatement(
                "SELECT uuid, conference_uuid, pod_name, seat_index, user_uuid, type, detail, occurred_at " +
                "FROM sandbox_incidents WHERE conference_uuid = ? ORDER BY occurred_at DESC")) {
            stmt.setString(1, conferenceUuid);
            try (final ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    incidents.add(new SandboxIncident(
                        rs.getString("uuid"),
                        rs.getString("conference_uuid"),
                        rs.getString("pod_name"),
                        rs.getInt("seat_index"),
                        rs.getString("user_uuid"),
                        rs.getString("type"),
                        rs.getString("detail"),
                        Instant.parse(rs.getString("occurred_at"))
                    ));
                }
            }
        } catch (final SQLException e) {
            throw new RuntimeException("Failed to find sandbox incidents by conference", e);
        }
        return incidents;
    }
}
