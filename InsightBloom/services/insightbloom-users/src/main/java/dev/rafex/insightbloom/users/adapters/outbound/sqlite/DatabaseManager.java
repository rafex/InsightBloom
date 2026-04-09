package dev.rafex.insightbloom.users.adapters.outbound.sqlite;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseManager {
    private final String dbPath;

    public DatabaseManager(String dbPath) {
        this.dbPath = dbPath;
    }

    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + dbPath);
    }

    public void initialize() {
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS users (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    uuid TEXT NOT NULL UNIQUE,
                    username TEXT NOT NULL UNIQUE,
                    display_name TEXT,
                    email TEXT,
                    role TEXT NOT NULL,
                    status TEXT NOT NULL,
                    created_at TEXT NOT NULL,
                    updated_at TEXT NOT NULL
                )
            """);
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS guest_users (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    uuid TEXT NOT NULL UNIQUE,
                    display_name TEXT,
                    device_fingerprint TEXT,
                    conference_uuid TEXT NOT NULL,
                    created_at TEXT NOT NULL
                )
            """);
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS tokens (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    uuid TEXT NOT NULL UNIQUE,
                    user_uuid TEXT,
                    guest_user_uuid TEXT,
                    token_kind TEXT NOT NULL,
                    token_value TEXT NOT NULL UNIQUE,
                    expires_at TEXT NOT NULL,
                    created_at TEXT NOT NULL,
                    revoked_at TEXT
                )
            """);
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS conferences (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    uuid TEXT NOT NULL UNIQUE,
                    friendly_id TEXT NOT NULL UNIQUE,
                    name TEXT NOT NULL,
                    created_by_user_uuid TEXT NOT NULL,
                    status TEXT NOT NULL,
                    created_at TEXT NOT NULL,
                    updated_at TEXT NOT NULL,
                    latitude REAL,
                    longitude REAL
                )
            """);
            // Migrations for existing databases
            try { stmt.executeUpdate("ALTER TABLE conferences ADD COLUMN latitude REAL"); } catch (SQLException ignore) {}
            try { stmt.executeUpdate("ALTER TABLE conferences ADD COLUMN longitude REAL"); } catch (SQLException ignore) {}
            // Seed a default organizer for PoC
            stmt.executeUpdate("""
                INSERT OR IGNORE INTO users (uuid, username, display_name, email, role, status, created_at, updated_at)
                VALUES ('00000000-0000-0000-0000-000000000001', 'admin', 'Admin', 'admin@insightbloom.dev',
                        'ORGANIZER', 'ACTIVE', datetime('now'), datetime('now'))
            """);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize database", e);
        }
    }
}
