package dev.rafex.insightbloom.users.adapters.outbound.sqlite;

import dev.rafex.insightbloom.users.domain.model.Notification;
import dev.rafex.insightbloom.users.domain.ports.NotificationRepository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SqliteNotificationRepository implements NotificationRepository {
    private final DatabaseManager database;

    public SqliteNotificationRepository(final DatabaseManager database) {
        this.database = database;
    }

    @Override
    public Notification save(final Notification notification) {
        try (var c = database.getConnection(); var ps = c.prepareStatement("""
                INSERT INTO notifications (uuid, user_uuid, type, title, body, link_url, created_at, read_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)""")) {
            ps.setString(1, notification.getUuid());
            ps.setString(2, notification.getUserUuid());
            ps.setString(3, notification.getType());
            ps.setString(4, notification.getTitle());
            ps.setString(5, notification.getBody());
            ps.setString(6, notification.getLinkUrl());
            ps.setString(7, notification.getCreatedAt().toString());
            ps.setString(8, notification.getReadAt() == null ? null : notification.getReadAt().toString());
            ps.executeUpdate();
            return notification;
        } catch (final SQLException e) {
            throw new RuntimeException("Failed to save notification", e);
        }
    }

    @Override
    public List<Notification> findByUser(final String userUuid, final int limit, final int offset) {
        try (var c = database.getConnection(); var ps = c.prepareStatement("""
                SELECT uuid, user_uuid, type, title, body, link_url, created_at, read_at
                FROM notifications WHERE user_uuid = ?
                ORDER BY created_at DESC LIMIT ? OFFSET ?""")) {
            ps.setString(1, userUuid);
            ps.setInt(2, limit);
            ps.setInt(3, offset);
            try (var rs = ps.executeQuery()) {
                final List<Notification> result = new ArrayList<>();
                while (rs.next()) result.add(read(rs));
                return result;
            }
        } catch (final SQLException e) {
            throw new RuntimeException("Failed to list notifications", e);
        }
    }

    @Override
    public int countUnread(final String userUuid) {
        try (var c = database.getConnection(); var ps = c.prepareStatement(
                "SELECT COUNT(*) FROM notifications WHERE user_uuid = ? AND read_at IS NULL")) {
            ps.setString(1, userUuid);
            try (var rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (final SQLException e) {
            throw new RuntimeException("Failed to count unread notifications", e);
        }
    }

    @Override
    public Optional<Notification> findByUuid(final String uuid) {
        try (var c = database.getConnection(); var ps = c.prepareStatement("""
                SELECT uuid, user_uuid, type, title, body, link_url, created_at, read_at
                FROM notifications WHERE uuid = ?""")) {
            ps.setString(1, uuid);
            try (var rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(read(rs));
            }
        } catch (final SQLException e) {
            throw new RuntimeException("Failed to read notification", e);
        }
    }

    @Override
    public boolean markRead(final String uuid, final String userUuid, final Instant readAt) {
        try (var c = database.getConnection(); var ps = c.prepareStatement("""
                UPDATE notifications SET read_at = ?
                WHERE uuid = ? AND user_uuid = ? AND read_at IS NULL""")) {
            ps.setString(1, readAt.toString());
            ps.setString(2, uuid);
            ps.setString(3, userUuid);
            return ps.executeUpdate() > 0;
        } catch (final SQLException e) {
            throw new RuntimeException("Failed to mark notification read", e);
        }
    }

    private Notification read(final ResultSet rs) throws SQLException {
        final String readAt = rs.getString("read_at");
        return new Notification(
                rs.getString("uuid"),
                rs.getString("user_uuid"),
                rs.getString("type"),
                rs.getString("title"),
                rs.getString("body"),
                rs.getString("link_url"),
                Instant.parse(rs.getString("created_at")),
                readAt == null ? null : Instant.parse(readAt));
    }
}
