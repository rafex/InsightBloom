package dev.rafex.insightbloom.users.adapters.outbound.sqlite;

import dev.rafex.insightbloom.common.migration.ColumnMigrationHelper;
import dev.rafex.insightbloom.common.sqlite.SqliteConnectionProvider;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseManager {
    private final SqliteConnectionProvider provider;

    public DatabaseManager(String dbPath) {
        this.provider = new SqliteConnectionProvider(dbPath);
    }

    public Connection getConnection() throws SQLException {
        return provider.getConnection();
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
                    password_hash TEXT,
                    created_at TEXT NOT NULL,
                    updated_at TEXT NOT NULL
                )
            """);
            // Migrate: add password_hash if existing DB lacks the column
            try { stmt.executeUpdate("ALTER TABLE users ADD COLUMN password_hash TEXT"); }
            catch (SQLException ignored) { /* column already exists */ }
            ColumnMigrationHelper.addColumnIfMissing(conn, "users", "phone", "TEXT");
            ColumnMigrationHelper.addColumnIfMissing(conn, "users", "social_links", "TEXT");
            ColumnMigrationHelper.addColumnIfMissing(conn, "users", "email_verified", "INTEGER NOT NULL DEFAULT 0");
            ColumnMigrationHelper.addColumnIfMissing(conn, "users", "phone_verified", "INTEGER NOT NULL DEFAULT 0");
            ColumnMigrationHelper.addColumnIfMissing(conn, "users", "first_name", "TEXT");
            ColumnMigrationHelper.addColumnIfMissing(conn, "users", "last_name", "TEXT");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_users_email ON users(email)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_users_phone ON users(phone)");

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
                    expires_at TEXT,
                    latitude REAL,
                    longitude REAL,
                    event_date TEXT,
                    venue TEXT,
                    start_time TEXT,
                    end_time TEXT,
                    name_auto_generated INTEGER NOT NULL DEFAULT 0
                )
            """);
            // Migrations for existing databases
            try { stmt.executeUpdate("ALTER TABLE conferences ADD COLUMN expires_at TEXT"); } catch (SQLException ignored) {}
            try { stmt.executeUpdate("ALTER TABLE conferences ADD COLUMN latitude REAL"); } catch (SQLException ignored) {}
            try { stmt.executeUpdate("ALTER TABLE conferences ADD COLUMN longitude REAL"); } catch (SQLException ignored) {}
            try { stmt.executeUpdate("ALTER TABLE conferences ADD COLUMN event_date TEXT"); } catch (SQLException ignored) {}
            try { stmt.executeUpdate("ALTER TABLE conferences ADD COLUMN start_time TEXT"); } catch (SQLException ignored) {}
            try { stmt.executeUpdate("ALTER TABLE conferences ADD COLUMN end_time TEXT"); } catch (SQLException ignored) {}
            try { stmt.executeUpdate("ALTER TABLE conferences ADD COLUMN venue TEXT"); } catch (SQLException ignored) {}
            try {
                stmt.executeUpdate("ALTER TABLE conferences ADD COLUMN name_auto_generated INTEGER NOT NULL DEFAULT 0");
            } catch (SQLException ignored) {}

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS otp_codes (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    uuid TEXT NOT NULL UNIQUE,
                    identifier TEXT NOT NULL,
                    channel TEXT NOT NULL,
                    code TEXT NOT NULL,
                    expires_at TEXT NOT NULL,
                    consumed INTEGER NOT NULL DEFAULT 0,
                    created_at TEXT NOT NULL
                )
            """);
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_otp_identifier ON otp_codes(identifier)");

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS conference_memberships (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    uuid TEXT NOT NULL UNIQUE,
                    user_uuid TEXT NOT NULL,
                    conference_uuid TEXT NOT NULL,
                    conference_name_snapshot TEXT,
                    conference_friendly_id_snapshot TEXT,
                    joined_at TEXT NOT NULL,
                    UNIQUE(user_uuid, conference_uuid)
                )
            """);
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_membership_user ON conference_memberships(user_uuid)");

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS certificate_settings (
                    id INTEGER PRIMARY KEY CHECK (id = 1),
                    logo_base64 TEXT,
                    font_family TEXT NOT NULL DEFAULT 'HELVETICA',
                    title_font_size INTEGER NOT NULL DEFAULT 28,
                    body_font_size INTEGER NOT NULL DEFAULT 14,
                    primary_color_hex TEXT NOT NULL DEFAULT '#1e1b4b',
                    show_venue INTEGER NOT NULL DEFAULT 1,
                    show_schedule INTEGER NOT NULL DEFAULT 1,
                    show_issued_date INTEGER NOT NULL DEFAULT 1,
                    updated_at TEXT
                )
            """);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize database", e);
        }
    }
}
