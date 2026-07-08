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
                    name_auto_generated INTEGER NOT NULL DEFAULT 0,
                    presentation_source_url TEXT,
                    flyer_base64 TEXT
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
            try {
                stmt.executeUpdate("ALTER TABLE conferences ADD COLUMN presentation_source_url TEXT");
            } catch (SQLException ignored) {}
            try {
                stmt.executeUpdate("ALTER TABLE conferences ADD COLUMN flyer_base64 TEXT");
            } catch (SQLException ignored) {}

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS timezones (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    iana_name TEXT NOT NULL UNIQUE,
                    label TEXT NOT NULL,
                    utc_offset_minutes INTEGER NOT NULL,
                    is_default INTEGER NOT NULL DEFAULT 0
                )
            """);
            seedTimezones(stmt);

            try {
                stmt.executeUpdate("ALTER TABLE conferences ADD COLUMN timezone_id INTEGER");
            } catch (SQLException ignored) {}
            try {
                stmt.executeUpdate("ALTER TABLE conferences ADD COLUMN reminder_sent_at TEXT");
            } catch (SQLException ignored) {}
            try {
                stmt.executeUpdate("ALTER TABLE conferences ADD COLUMN seating_mode TEXT NOT NULL DEFAULT 'NONE'");
            } catch (SQLException ignored) {}
            try {
                stmt.executeUpdate("ALTER TABLE conferences ADD COLUMN capacity INTEGER");
            } catch (SQLException ignored) {}
            try {
                stmt.executeUpdate("ALTER TABLE conferences ADD COLUMN reserved_count INTEGER NOT NULL DEFAULT 0");
            } catch (SQLException ignored) {}
            try {
                stmt.executeUpdate("ALTER TABLE conferences ADD COLUMN venue_map_base64 TEXT");
            } catch (SQLException ignored) {}

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS reservations (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    uuid TEXT NOT NULL UNIQUE,
                    conference_uuid TEXT NOT NULL,
                    user_uuid TEXT NOT NULL,
                    seat_uuid TEXT,
                    ticket_code TEXT NOT NULL UNIQUE,
                    status TEXT NOT NULL,
                    created_at TEXT NOT NULL,
                    checked_in_at TEXT,
                    UNIQUE(conference_uuid, seat_uuid)
                )
            """);
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_reservations_conference ON reservations(conference_uuid)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_reservations_user ON reservations(user_uuid)");

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

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS download_events (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    uuid TEXT NOT NULL UNIQUE,
                    conference_uuid TEXT NOT NULL,
                    kind TEXT NOT NULL,
                    created_at TEXT NOT NULL
                )
            """);
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_download_conference ON download_events(conference_uuid, kind)");
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize database", e);
        }
    }

    /**
     * Catálogo fijo de zonas horarias (offset fijo, sin horario de verano) para no depender de
     * una librería de zonas horarias ni de reglas de DST al calcular recordatorios o generar
     * archivos .ics. GMT-6 (América/Ciudad de México) es la zona por defecto.
     */
    private void seedTimezones(final Statement stmt) throws SQLException {
        final Object[][] zones = {
            {"Etc/GMT+12", "GMT-12", -720, 0},
            {"Pacific/Midway", "GMT-11", -660, 0},
            {"Pacific/Honolulu", "GMT-10 (Hawái)", -600, 0},
            {"America/Anchorage", "GMT-9 (Alaska)", -540, 0},
            {"America/Tijuana", "GMT-8 (Pacífico)", -480, 0},
            {"America/Denver", "GMT-7 (Montaña)", -420, 0},
            {"America/Mexico_City", "GMT-6 (Ciudad de México, Centroamérica)", -360, 1},
            {"America/Chicago", "GMT-6 (Central EE. UU.)", -360, 0},
            {"America/Bogota", "GMT-5 (Colombia, Perú, Ecuador)", -300, 0},
            {"America/New_York", "GMT-5 (Este EE. UU.)", -300, 0},
            {"America/Santiago", "GMT-4 (Chile)", -240, 0},
            {"America/Caracas", "GMT-4 (Venezuela)", -240, 0},
            {"America/Argentina/Buenos_Aires", "GMT-3 (Argentina, Uruguay)", -180, 0},
            {"America/Sao_Paulo", "GMT-3 (Brasil)", -180, 0},
            {"Atlantic/Azores", "GMT-1", -60, 0},
            {"Europe/London", "GMT+0 (Londres)", 0, 0},
            {"Europe/Madrid", "GMT+1 (España, Europa Central)", 60, 0},
            {"Europe/Athens", "GMT+2 (Europa del Este)", 120, 0},
            {"Asia/Dubai", "GMT+4", 240, 0},
            {"Asia/Kolkata", "GMT+5:30 (India)", 330, 0},
            {"Asia/Shanghai", "GMT+8 (China)", 480, 0},
            {"Asia/Tokyo", "GMT+9 (Japón)", 540, 0},
            {"Australia/Sydney", "GMT+10 (Australia Este)", 600, 0}
        };
        final String sql = "INSERT OR IGNORE INTO timezones (iana_name, label, utc_offset_minutes, is_default) "
                + "VALUES (?, ?, ?, ?)";
        try (var ps = stmt.getConnection().prepareStatement(sql)) {
            for (final Object[] z : zones) {
                ps.setString(1, (String) z[0]);
                ps.setString(2, (String) z[1]);
                ps.setInt(3, (Integer) z[2]);
                ps.setInt(4, (Integer) z[3]);
                ps.executeUpdate();
            }
        }
    }
}
