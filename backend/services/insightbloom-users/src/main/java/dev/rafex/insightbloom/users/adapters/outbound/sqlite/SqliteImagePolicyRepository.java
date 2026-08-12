package dev.rafex.insightbloom.users.adapters.outbound.sqlite;

import dev.rafex.insightbloom.users.domain.model.ImagePolicy;
import dev.rafex.insightbloom.users.domain.ports.ImagePolicyRepository;

import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;

public class SqliteImagePolicyRepository implements ImagePolicyRepository {
    private final DatabaseManager database;

    public SqliteImagePolicyRepository(final DatabaseManager database) {
        this.database = database;
    }

    @Override
    public Optional<ImagePolicy> findByConference(final String conferenceUuid) {
        try (var c = database.getConnection(); var ps = c.prepareStatement("""
                SELECT conference_uuid, allowed_images, blocked_images, updated_at
                FROM image_policies WHERE conference_uuid = ?""")) {
            ps.setString(1, conferenceUuid);
            try (var rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(read(rs));
            }
        } catch (final SQLException e) {
            throw new RuntimeException("Failed to read image policy", e);
        }
    }

    @Override
    public ImagePolicy save(final ImagePolicy policy) {
        try (var c = database.getConnection(); var ps = c.prepareStatement("""
                INSERT INTO image_policies (conference_uuid, allowed_images, blocked_images, updated_at)
                VALUES (?, ?, ?, ?)
                ON CONFLICT(conference_uuid) DO UPDATE SET
                    allowed_images = excluded.allowed_images,
                    blocked_images = excluded.blocked_images,
                    updated_at = excluded.updated_at""")) {
            ps.setString(1, policy.conferenceUuid());
            ps.setString(2, policy.allowedImages());
            ps.setString(3, policy.blockedImages());
            ps.setString(4, policy.updatedAt().toString());
            ps.executeUpdate();
            return policy;
        } catch (final SQLException e) {
            throw new RuntimeException("Failed to save image policy", e);
        }
    }

    @Override
    public void deleteByConference(final String conferenceUuid) {
        try (var c = database.getConnection(); var ps = c.prepareStatement(
                "DELETE FROM image_policies WHERE conference_uuid = ?")) {
            ps.setString(1, conferenceUuid);
            ps.executeUpdate();
        } catch (final SQLException e) {
            throw new RuntimeException("Failed to delete image policy", e);
        }
    }

    private ImagePolicy read(final java.sql.ResultSet rs) throws SQLException {
        return new ImagePolicy(
                rs.getString("conference_uuid"),
                rs.getString("allowed_images"),
                rs.getString("blocked_images"),
                Instant.parse(rs.getString("updated_at")));
    }
}
