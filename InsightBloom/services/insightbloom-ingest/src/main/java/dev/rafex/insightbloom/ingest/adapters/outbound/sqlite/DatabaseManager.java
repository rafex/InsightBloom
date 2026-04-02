package dev.rafex.insightbloom.ingest.adapters.outbound.sqlite;
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
                CREATE TABLE IF NOT EXISTS messages (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    uuid TEXT NOT NULL UNIQUE,
                    conference_uuid TEXT NOT NULL,
                    author_uuid TEXT NOT NULL,
                    author_kind TEXT NOT NULL,
                    device_fingerprint TEXT,
                    message_type TEXT NOT NULL,
                    source_type TEXT NOT NULL,
                    received_at TEXT NOT NULL,
                    word_original TEXT NOT NULL,
                    word_normalized TEXT NOT NULL,
                    word_canonical TEXT NOT NULL,
                    word_intent TEXT,
                    detail_original TEXT,
                    detail_visible TEXT,
                    detail_intent TEXT,
                    word_status TEXT NOT NULL DEFAULT 'VISIBLE',
                    detail_status TEXT NOT NULL DEFAULT 'VISIBLE',
                    is_visible INTEGER NOT NULL DEFAULT 1,
                    created_at TEXT NOT NULL,
                    updated_at TEXT NOT NULL
                )""");
        } catch (SQLException e) { throw new RuntimeException("Failed to init ingest db", e); }
    }
}
