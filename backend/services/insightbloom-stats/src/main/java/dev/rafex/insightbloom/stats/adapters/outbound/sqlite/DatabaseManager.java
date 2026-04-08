package dev.rafex.insightbloom.stats.adapters.outbound.sqlite;
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
                CREATE TABLE IF NOT EXISTS word_stats (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    uuid TEXT NOT NULL UNIQUE,
                    conference_uuid TEXT NOT NULL,
                    message_type TEXT NOT NULL,
                    word_normalized TEXT NOT NULL,
                    word_canonical TEXT NOT NULL,
                    count_total INTEGER NOT NULL DEFAULT 0,
                    count_visible INTEGER NOT NULL DEFAULT 0,
                    count_censored INTEGER NOT NULL DEFAULT 0,
                    score_intent REAL NOT NULL DEFAULT 1.0,
                    relevance_score REAL NOT NULL DEFAULT 0.0,
                    updated_at TEXT NOT NULL,
                    UNIQUE(conference_uuid, message_type, word_normalized)
                )""");
        } catch (SQLException e) { throw new RuntimeException("Failed to init stats db", e); }
    }
}
