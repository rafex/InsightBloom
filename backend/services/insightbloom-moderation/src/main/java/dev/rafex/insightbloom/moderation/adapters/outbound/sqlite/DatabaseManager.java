package dev.rafex.insightbloom.moderation.adapters.outbound.sqlite;
import java.sql.*;
public class DatabaseManager {
    private final String dbPath;
    public DatabaseManager(String dbPath) { this.dbPath = dbPath; }
    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + dbPath);
    }
    public void initialize() {
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS blocked_terms (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    uuid TEXT NOT NULL UNIQUE,
                    scope TEXT NOT NULL,
                    term_normalized TEXT NOT NULL UNIQUE,
                    status TEXT NOT NULL DEFAULT 'active',
                    created_by_user_uuid TEXT,
                    created_at TEXT NOT NULL,
                    updated_at TEXT NOT NULL
                )""");
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS moderation_words (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    uuid TEXT NOT NULL UNIQUE,
                    conference_uuid TEXT NOT NULL,
                    word_normalized TEXT NOT NULL,
                    word_canonical TEXT NOT NULL,
                    status TEXT NOT NULL DEFAULT 'VISIBLE',
                    reason TEXT,
                    edited_value TEXT,
                    updated_by_user_uuid TEXT,
                    updated_at TEXT NOT NULL
                )""");
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS moderation_messages (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    uuid TEXT NOT NULL UNIQUE,
                    message_uuid TEXT NOT NULL UNIQUE,
                    conference_uuid TEXT NOT NULL,
                    word_text TEXT,
                    detail_text TEXT,
                    word_status TEXT NOT NULL DEFAULT 'VISIBLE',
                    detail_status TEXT NOT NULL DEFAULT 'VISIBLE',
                    reason TEXT,
                    edited_word_value TEXT,
                    edited_detail_value TEXT,
                    updated_by_user_uuid TEXT,
                    updated_at TEXT NOT NULL
                )""");
            try { stmt.executeUpdate("ALTER TABLE moderation_messages ADD COLUMN word_text TEXT"); } catch (SQLException ignored) {}
            try { stmt.executeUpdate("ALTER TABLE moderation_messages ADD COLUMN detail_text TEXT"); } catch (SQLException ignored) {}
            // Seed some blocked terms for PoC
            stmt.executeUpdate("""
                INSERT OR IGNORE INTO blocked_terms (uuid, scope, term_normalized, status, created_at, updated_at)
                VALUES
                  ('bt-0001', 'all', 'spam', 'active', datetime('now'), datetime('now')),
                  ('bt-0002', 'all', 'offtopic', 'active', datetime('now'), datetime('now'))
            """);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize moderation database", e);
        }
    }
}
