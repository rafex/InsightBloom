package dev.rafex.insightbloom.users.adapters.outbound.sqlite;

import dev.rafex.insightbloom.users.domain.model.WorkspaceZipJob;
import dev.rafex.insightbloom.users.domain.ports.WorkspaceZipJobRepository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SqliteWorkspaceZipJobRepository implements WorkspaceZipJobRepository {
    private final DatabaseManager database;

    public SqliteWorkspaceZipJobRepository(final DatabaseManager database) {
        this.database = database;
    }

    @Override
    public WorkspaceZipJob save(final WorkspaceZipJob job) {
        try (var c = database.getConnection(); var ps = c.prepareStatement("""
                INSERT INTO workspace_zip_jobs
                    (uuid, conference_uuid, sandbox_uuid, user_uuid, status, download_token,
                     created_at, ready_at, expires_at, error_message)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(uuid) DO UPDATE SET
                    status = excluded.status,
                    download_token = excluded.download_token,
                    ready_at = excluded.ready_at,
                    expires_at = excluded.expires_at,
                    error_message = excluded.error_message""")) {
            ps.setString(1, job.uuid());
            ps.setString(2, job.conferenceUuid());
            ps.setString(3, job.sandboxUuid());
            ps.setString(4, job.userUuid());
            ps.setString(5, job.status());
            ps.setString(6, job.downloadToken());
            ps.setString(7, job.createdAt().toString());
            ps.setString(8, job.readyAt() == null ? null : job.readyAt().toString());
            ps.setString(9, job.expiresAt() == null ? null : job.expiresAt().toString());
            ps.setString(10, job.errorMessage());
            ps.executeUpdate();
            return job;
        } catch (final SQLException e) {
            throw new RuntimeException("Failed to save workspace zip job", e);
        }
    }

    @Override
    public Optional<WorkspaceZipJob> findByUuid(final String uuid) {
        try (var c = database.getConnection(); var ps = c.prepareStatement("""
                SELECT uuid, conference_uuid, sandbox_uuid, user_uuid, status, download_token,
                       created_at, ready_at, expires_at, error_message
                FROM workspace_zip_jobs WHERE uuid = ?""")) {
            ps.setString(1, uuid);
            try (var rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(read(rs));
            }
        } catch (final SQLException e) {
            throw new RuntimeException("Failed to read workspace zip job", e);
        }
    }

    @Override
    public List<WorkspaceZipJob> findExpired(final Instant now) {
        try (var c = database.getConnection(); var ps = c.prepareStatement("""
                SELECT uuid, conference_uuid, sandbox_uuid, user_uuid, status, download_token,
                       created_at, ready_at, expires_at, error_message
                FROM workspace_zip_jobs WHERE expires_at IS NOT NULL AND expires_at < ?""")) {
            ps.setString(1, now.toString());
            try (var rs = ps.executeQuery()) {
                final List<WorkspaceZipJob> result = new ArrayList<>();
                while (rs.next()) result.add(read(rs));
                return result;
            }
        } catch (final SQLException e) {
            throw new RuntimeException("Failed to list expired workspace zip jobs", e);
        }
    }

    @Override
    public void deleteByUuid(final String uuid) {
        try (var c = database.getConnection(); var ps = c.prepareStatement(
                "DELETE FROM workspace_zip_jobs WHERE uuid = ?")) {
            ps.setString(1, uuid);
            ps.executeUpdate();
        } catch (final SQLException e) {
            throw new RuntimeException("Failed to delete workspace zip job", e);
        }
    }

    private WorkspaceZipJob read(final ResultSet rs) throws SQLException {
        final String readyAt = rs.getString("ready_at");
        final String expiresAt = rs.getString("expires_at");
        return new WorkspaceZipJob(
                rs.getString("uuid"),
                rs.getString("conference_uuid"),
                rs.getString("sandbox_uuid"),
                rs.getString("user_uuid"),
                rs.getString("status"),
                rs.getString("download_token"),
                Instant.parse(rs.getString("created_at")),
                readyAt == null ? null : Instant.parse(readyAt),
                expiresAt == null ? null : Instant.parse(expiresAt),
                rs.getString("error_message"));
    }
}
