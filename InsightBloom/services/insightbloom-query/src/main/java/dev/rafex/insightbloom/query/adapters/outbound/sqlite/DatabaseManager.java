package dev.rafex.insightbloom.query.adapters.outbound.sqlite;
import java.sql.*;
public class DatabaseManager {
    private final String dbPath;
    public DatabaseManager(String dbPath) { this.dbPath = dbPath; }
    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + dbPath);
    }
    public void initialize() {
        try (Connection c = getConnection(); Statement stmt = c.createStatement()) {
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS cloud_words (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    uuid TEXT NOT NULL UNIQUE,
                    conference_uuid TEXT NOT NULL,
                    message_type TEXT NOT NULL,
                    word_normalized TEXT NOT NULL,
                    word_canonical TEXT NOT NULL,
                    relevance_score REAL NOT NULL DEFAULT 0.0,
                    message_count INTEGER NOT NULL DEFAULT 0,
                    first_seen_at TEXT NOT NULL,
                    last_seen_at TEXT NOT NULL,
                    is_visible INTEGER NOT NULL DEFAULT 1,
                    updated_at TEXT NOT NULL,
                    UNIQUE(conference_uuid, message_type, word_normalized)
                )""");
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS word_timeline (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    uuid TEXT NOT NULL UNIQUE,
                    conference_uuid TEXT NOT NULL,
                    word_normalized TEXT NOT NULL,
                    message_uuid TEXT NOT NULL UNIQUE,
                    message_type TEXT NOT NULL,
                    author_label TEXT,
                    author_kind TEXT NOT NULL,
                    detail_visible TEXT,
                    received_at TEXT NOT NULL,
                    is_visible INTEGER NOT NULL DEFAULT 1,
                    updated_at TEXT NOT NULL
                )""");
        } catch (SQLException e) { throw new RuntimeException("Failed to init query db", e); }
    }
}
