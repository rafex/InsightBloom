package dev.rafex.insightbloom.users.adapters.outbound.sqlite;

import dev.rafex.insightbloom.users.domain.model.SandboxAppPreview;
import dev.rafex.insightbloom.users.domain.ports.SandboxAppPreviewRepository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;

public class SqliteSandboxAppPreviewRepository implements SandboxAppPreviewRepository {
    private final DatabaseManager database;

    public SqliteSandboxAppPreviewRepository(final DatabaseManager database) {
        this.database = database;
    }

    @Override
    public Optional<SandboxAppPreview> findByUuid(final String uuid) {
        try (var c = database.getConnection(); var ps = c.prepareStatement("""
                SELECT uuid, conference_uuid, user_uuid, pod_name, target_port, access_token,
                       created_at, expires_at
                FROM sandbox_app_previews WHERE uuid = ?""")) {
            ps.setString(1, uuid);
            try (var rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(read(rs));
            }
        } catch (final SQLException e) {
            throw new RuntimeException("Failed to read sandbox app preview", e);
        }
    }

    @Override
    public Optional<SandboxAppPreview> findByConferenceAndUser(final String conferenceUuid, final String userUuid) {
        try (var c = database.getConnection(); var ps = c.prepareStatement("""
                SELECT uuid, conference_uuid, user_uuid, pod_name, target_port, access_token,
                       created_at, expires_at
                FROM sandbox_app_previews WHERE conference_uuid = ? AND user_uuid = ?""")) {
            ps.setString(1, conferenceUuid);
            ps.setString(2, userUuid);
            try (var rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(read(rs));
            }
        } catch (final SQLException e) {
            throw new RuntimeException("Failed to read sandbox app preview", e);
        }
    }

    @Override
    public SandboxAppPreview save(final SandboxAppPreview preview) {
        try (var c = database.getConnection(); var ps = c.prepareStatement("""
                INSERT INTO sandbox_app_previews
                    (uuid, conference_uuid, user_uuid, pod_name, target_port, access_token, created_at, expires_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(conference_uuid, user_uuid) DO UPDATE SET
                    uuid = excluded.uuid,
                    pod_name = excluded.pod_name,
                    target_port = excluded.target_port,
                    access_token = excluded.access_token,
                    created_at = excluded.created_at,
                    expires_at = excluded.expires_at""")) {
            ps.setString(1, preview.uuid());
            ps.setString(2, preview.conferenceUuid());
            ps.setString(3, preview.userUuid());
            ps.setString(4, preview.podName());
            ps.setInt(5, preview.targetPort());
            ps.setString(6, preview.accessToken());
            ps.setString(7, preview.createdAt().toString());
            ps.setString(8, preview.expiresAt().toString());
            ps.executeUpdate();
            return preview;
        } catch (final SQLException e) {
            throw new RuntimeException("Failed to save sandbox app preview", e);
        }
    }

    @Override
    public void deleteByUuid(final String uuid) {
        try (var c = database.getConnection(); var ps = c.prepareStatement(
                "DELETE FROM sandbox_app_previews WHERE uuid = ?")) {
            ps.setString(1, uuid);
            ps.executeUpdate();
        } catch (final SQLException e) {
            throw new RuntimeException("Failed to delete sandbox app preview", e);
        }
    }

    private SandboxAppPreview read(final ResultSet rs) throws SQLException {
        return new SandboxAppPreview(
                rs.getString("uuid"),
                rs.getString("conference_uuid"),
                rs.getString("user_uuid"),
                rs.getString("pod_name"),
                rs.getInt("target_port"),
                rs.getString("access_token"),
                Instant.parse(rs.getString("created_at")),
                Instant.parse(rs.getString("expires_at")));
    }
}
