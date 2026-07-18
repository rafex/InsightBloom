package dev.rafex.insightbloom.users.adapters.outbound.sqlite;

import dev.rafex.insightbloom.common.migration.ColumnMigrationHelper;
import dev.rafex.insightbloom.common.sqlite.SqliteConnectionProvider;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
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
            ColumnMigrationHelper.addColumnIfMissing(conn, "users", "last_login_at", "TEXT");
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
            try {
                stmt.executeUpdate("ALTER TABLE conferences ADD COLUMN sandbox_variant TEXT");
            } catch (SQLException ignored) {}
            try {
                stmt.executeUpdate("ALTER TABLE conferences ADD COLUMN sandbox_pool_size INTEGER");
            } catch (SQLException ignored) {}
            try {
                stmt.executeUpdate("ALTER TABLE conferences ADD COLUMN sandbox_internet_enabled INTEGER NOT NULL DEFAULT 0");
            } catch (SQLException ignored) {}
            try {
                stmt.executeUpdate("ALTER TABLE conferences ADD COLUMN sandbox_extra_packages TEXT");
            } catch (SQLException ignored) {}
            try {
                stmt.executeUpdate("ALTER TABLE conferences ADD COLUMN sandbox_remote_git_url TEXT");
            } catch (SQLException ignored) {}
            try {
                stmt.executeUpdate("ALTER TABLE conferences ADD COLUMN sandbox_jvm_heap_mb INTEGER");
            } catch (SQLException ignored) {}
            try {
                stmt.executeUpdate("ALTER TABLE conferences ADD COLUMN sandbox_seats_per_pod INTEGER");
            } catch (SQLException ignored) {}
            try {
                stmt.executeUpdate("ALTER TABLE conferences ADD COLUMN sandbox_cli_pool_size INTEGER");
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
                CREATE TABLE IF NOT EXISTS venue_seats (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    uuid TEXT NOT NULL UNIQUE,
                    conference_uuid TEXT NOT NULL,
                    label TEXT NOT NULL,
                    x REAL NOT NULL,
                    y REAL NOT NULL,
                    created_at TEXT NOT NULL
                )
            """);
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_venue_seats_conference ON venue_seats(conference_uuid)");

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
            ColumnMigrationHelper.addColumnIfMissing(conn, "download_events", "user_uuid", "TEXT");

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS event_types (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    uuid TEXT NOT NULL UNIQUE,
                    key TEXT NOT NULL UNIQUE,
                    name TEXT NOT NULL,
                    description TEXT,
                    capabilities TEXT NOT NULL,
                    active INTEGER NOT NULL DEFAULT 1,
                    created_at TEXT NOT NULL,
                    updated_at TEXT NOT NULL
                )
            """);
            seedEventTypes(stmt);

            try {
                stmt.executeUpdate("ALTER TABLE conferences ADD COLUMN event_type_key TEXT NOT NULL DEFAULT 'conference'");
            } catch (SQLException ignored) {}
            try {
                stmt.executeUpdate("ALTER TABLE conferences ADD COLUMN notes_purged_at TEXT");
            } catch (SQLException ignored) {}
            try {
                stmt.executeUpdate("ALTER TABLE conferences ADD COLUMN diagram_xml TEXT");
            } catch (SQLException ignored) {}
            try {
                stmt.executeUpdate("ALTER TABLE conferences ADD COLUMN diagram_updated_at TEXT");
            } catch (SQLException ignored) {}
            try {
                stmt.executeUpdate("ALTER TABLE conferences ADD COLUMN diagram_purged_at TEXT");
            } catch (SQLException ignored) {}

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS roles (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    uuid TEXT NOT NULL UNIQUE,
                    key TEXT NOT NULL UNIQUE,
                    name TEXT NOT NULL,
                    description TEXT,
                    scope TEXT NOT NULL,
                    permissions TEXT NOT NULL,
                    active INTEGER NOT NULL DEFAULT 1,
                    created_at TEXT NOT NULL,
                    updated_at TEXT NOT NULL
                )
            """);
            seedRoles(stmt);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS event_roles (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    uuid TEXT NOT NULL UNIQUE,
                    event_uuid TEXT NOT NULL,
                    user_uuid TEXT NOT NULL,
                    role_key TEXT NOT NULL,
                    assigned_at TEXT NOT NULL,
                    UNIQUE(event_uuid, user_uuid)
                )
            """);
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_event_roles_event ON event_roles(event_uuid)");

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS platform_settings (
                    id INTEGER PRIMARY KEY CHECK (id = 1),
                    chat_ai_enabled INTEGER NOT NULL DEFAULT 1,
                    updated_at TEXT
                )
            """);
            try {
                stmt.executeUpdate("ALTER TABLE platform_settings ADD COLUMN chat_system_prompt TEXT");
            } catch (final SQLException ignored) { /* columna ya existe */ }
            try {
                stmt.executeUpdate("ALTER TABLE platform_settings ADD COLUMN chat_temperature REAL");
            } catch (final SQLException ignored) { /* columna ya existe */ }

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS sandbox_assignments (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    uuid TEXT NOT NULL UNIQUE,
                    conference_uuid TEXT NOT NULL,
                    sandbox_slot INTEGER NOT NULL,
                    user_uuid TEXT,
                    assigned_at TEXT,
                    created_at TEXT NOT NULL,
                    expires_at TEXT NOT NULL,
                    UNIQUE(conference_uuid, sandbox_slot)
                )
            """);
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_sandbox_conference ON sandbox_assignments(conference_uuid)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_sandbox_user ON sandbox_assignments(user_uuid)");
            migrateSandboxAssignmentsSeatIndex(conn);
            migrateSandboxAssignmentsVariant(conn);

            // Fase C (DEC-0025): incidentes de abuso de recursos en Pods "neovim" compartidos,
            // reportados por el seat-agent -- ver SandboxIncident.
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS sandbox_incidents (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    uuid TEXT NOT NULL UNIQUE,
                    conference_uuid TEXT NOT NULL,
                    pod_name TEXT NOT NULL,
                    seat_index INTEGER NOT NULL,
                    user_uuid TEXT,
                    type TEXT NOT NULL,
                    detail TEXT,
                    occurred_at TEXT NOT NULL
                )
            """);
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_sandbox_incidents_conference ON sandbox_incidents(conference_uuid)");

            backfillMissingTicketsForSimpleJoins(conn);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize database", e);
        }
    }

    /**
     * Unifica la inscripción a un evento en el boleto ({@code reservations}): antes solo se
     * emitía boleto automático para eventos en modo GENERAL, dejando a quienes se unieron a un
     * evento NONE (la mayoría, hoy) sin ningún registro de acceso a las herramientas del evento
     * (IDE, etc). Idempotente -- solo inserta boletos para memberships de eventos NONE que
     * todavía no tienen uno; en cada arranque no hace nada si ya se corrió antes.
     */
    private void backfillMissingTicketsForSimpleJoins(final Connection conn) throws SQLException {
        final String selectSql = """
            SELECT cm.user_uuid, cm.conference_uuid, cm.joined_at
            FROM conference_memberships cm
            JOIN conferences c ON c.uuid = cm.conference_uuid
            LEFT JOIN reservations r ON r.conference_uuid = cm.conference_uuid AND r.user_uuid = cm.user_uuid
            WHERE c.seating_mode = 'NONE' AND r.uuid IS NULL
        """;
        final String insertSql = """
            INSERT INTO reservations (uuid, conference_uuid, user_uuid, seat_uuid, ticket_code, status, created_at, checked_in_at)
            VALUES (?, ?, ?, NULL, ?, 'RESERVED', ?, NULL)
        """;
        try (Statement selectStmt = conn.createStatement();
             ResultSet rs = selectStmt.executeQuery(selectSql);
             PreparedStatement insertPs = conn.prepareStatement(insertSql)) {
            while (rs.next()) {
                insertPs.setString(1, java.util.UUID.randomUUID().toString());
                insertPs.setString(2, rs.getString("conference_uuid"));
                insertPs.setString(3, rs.getString("user_uuid"));
                insertPs.setString(4, java.util.UUID.randomUUID().toString());
                insertPs.setString(5, rs.getString("joined_at"));
                insertPs.executeUpdate();
            }
        }
    }

    /**
     * Pods "neovim" multi-asiento (2026-07): varios alumnos pueden compartir el mismo
     * {@code sandbox_slot} (mismo Pod), cada uno en su propio {@code seat_index}. El
     * {@code UNIQUE(conference_uuid, sandbox_slot)} original de la tabla lo impedía a proposito
     * (1 fila por pod) -- SQLite no permite ALTER TABLE para cambiar un UNIQUE existente, así que
     * hay que recrear la tabla completa cuando todavía tiene el constraint viejo. Idempotente:
     * se fija en el SQL de creación real (via sqlite_master) si el constraint viejo sigue ahí
     * antes de hacer nada; en una base ya migrada esto es un no-op.
     */
    private void migrateSandboxAssignmentsSeatIndex(final Connection conn) throws SQLException {
        ColumnMigrationHelper.addColumnIfMissing(conn, "sandbox_assignments", "seat_index", "INTEGER NOT NULL DEFAULT 0");
        String createSql = null;
        try (Statement s = conn.createStatement();
             ResultSet rs = s.executeQuery(
                 "SELECT sql FROM sqlite_master WHERE type='table' AND name='sandbox_assignments'")) {
            if (rs.next()) createSql = rs.getString("sql");
        }
        final boolean hasOldNarrowUnique = createSql != null
                && createSql.replaceAll("\\s+", " ").contains("UNIQUE(conference_uuid, sandbox_slot)");
        if (!hasOldNarrowUnique) {
            return;
        }
        try (Statement s = conn.createStatement()) {
            s.executeUpdate("""
                CREATE TABLE sandbox_assignments_new (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    uuid TEXT NOT NULL UNIQUE,
                    conference_uuid TEXT NOT NULL,
                    sandbox_slot INTEGER NOT NULL,
                    seat_index INTEGER NOT NULL DEFAULT 0,
                    user_uuid TEXT,
                    assigned_at TEXT,
                    created_at TEXT NOT NULL,
                    expires_at TEXT NOT NULL,
                    UNIQUE(conference_uuid, sandbox_slot, seat_index)
                )
            """);
            s.executeUpdate("""
                INSERT INTO sandbox_assignments_new
                    (id, uuid, conference_uuid, sandbox_slot, seat_index, user_uuid, assigned_at, created_at, expires_at)
                SELECT id, uuid, conference_uuid, sandbox_slot, seat_index, user_uuid, assigned_at, created_at, expires_at
                FROM sandbox_assignments
            """);
            s.executeUpdate("DROP TABLE sandbox_assignments");
            s.executeUpdate("ALTER TABLE sandbox_assignments_new RENAME TO sandbox_assignments");
            s.executeUpdate("CREATE INDEX IF NOT EXISTS idx_sandbox_conference ON sandbox_assignments(conference_uuid)");
            s.executeUpdate("CREATE INDEX IF NOT EXISTS idx_sandbox_user ON sandbox_assignments(user_uuid)");
        }
    }

    /**
     * Pools Web/CLI independientes (2026-07): cada Sandbox pertenece a una variante ("web" o
     * "cli", ver Sandbox.VARIANT_WEB/VARIANT_CLI), cada una con su propia numeracion de
     * sandbox_slot (0..poolSize-1 POR variante) -- el UNIQUE(conference_uuid, sandbox_slot,
     * seat_index) original permitiria que un slot "web" y un slot "cli" con el mismo numero
     * colisionen. Mismo patron que migrateSandboxAssignmentsSeatIndex (recrear tabla, SQLite no
     * permite ALTER un UNIQUE existente), idempotente vía sqlite_master. Filas existentes
     * (todas predatan el pool de CLI) quedan en 'web'.
     */
    private void migrateSandboxAssignmentsVariant(final Connection conn) throws SQLException {
        ColumnMigrationHelper.addColumnIfMissing(conn, "sandbox_assignments", "variant", "TEXT NOT NULL DEFAULT 'web'");
        String createSql = null;
        try (Statement s = conn.createStatement();
             ResultSet rs = s.executeQuery(
                 "SELECT sql FROM sqlite_master WHERE type='table' AND name='sandbox_assignments'")) {
            if (rs.next()) createSql = rs.getString("sql");
        }
        final boolean hasOldUnique = createSql != null
                && createSql.replaceAll("\\s+", " ").contains("UNIQUE(conference_uuid, sandbox_slot, seat_index)");
        if (!hasOldUnique) {
            return;
        }
        try (Statement s = conn.createStatement()) {
            s.executeUpdate("""
                CREATE TABLE sandbox_assignments_new (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    uuid TEXT NOT NULL UNIQUE,
                    conference_uuid TEXT NOT NULL,
                    sandbox_slot INTEGER NOT NULL,
                    seat_index INTEGER NOT NULL DEFAULT 0,
                    variant TEXT NOT NULL DEFAULT 'web',
                    user_uuid TEXT,
                    assigned_at TEXT,
                    created_at TEXT NOT NULL,
                    expires_at TEXT NOT NULL,
                    UNIQUE(conference_uuid, variant, sandbox_slot, seat_index)
                )
            """);
            s.executeUpdate("""
                INSERT INTO sandbox_assignments_new
                    (id, uuid, conference_uuid, sandbox_slot, seat_index, variant, user_uuid, assigned_at, created_at, expires_at)
                SELECT id, uuid, conference_uuid, sandbox_slot, seat_index, variant, user_uuid, assigned_at, created_at, expires_at
                FROM sandbox_assignments
            """);
            s.executeUpdate("DROP TABLE sandbox_assignments");
            s.executeUpdate("ALTER TABLE sandbox_assignments_new RENAME TO sandbox_assignments");
            s.executeUpdate("CREATE INDEX IF NOT EXISTS idx_sandbox_conference ON sandbox_assignments(conference_uuid)");
            s.executeUpdate("CREATE INDEX IF NOT EXISTS idx_sandbox_user ON sandbox_assignments(user_uuid)");
        }
    }

    /**
     * Sembrado inicial del catálogo de roles (DEC-0021): 3 de alcance PLATFORM
     * (system_admin, event_type_admin, organizer) y 5 de alcance EVENT (host, moderator,
     * checkin_staff, guest_presenter, survey_manager). El creador de un evento recibe la fila
     * `host` automaticamente (ver CreateConferenceUseCase); `system_admin` tiene bypass total
     * sobre cualquier evento sin necesidad de una fila en event_roles (ver EventPermissionGuard).
     */
    private void seedRoles(final Statement stmt) throws SQLException {
        final Object[][] roles = {
            {"system_admin", "Administrador de sistema", "PLATFORM", "MANAGE_USERS,MANAGE_EVENT_TYPES"},
            {"event_type_admin", "Administrador de tipos de evento", "PLATFORM", "MANAGE_EVENT_TYPES"},
            {"organizer", "Organizador", "PLATFORM", "HOST_EVENT"},
            {"host", "Host/Anfitrión", "EVENT",
                "MANAGE_EVENT_SETTINGS,ASSIGN_EVENT_ROLES,MODERATE_CONTENT,CHECK_IN,MANAGE_PRESENTATION,MANAGE_SURVEY,MANAGE_CERTIFICATE,VIDEO_MODERATE"},
            {"moderator", "Moderador", "EVENT", "MODERATE_CONTENT,VIDEO_MODERATE"},
            {"checkin_staff", "Staff de acceso", "EVENT", "CHECK_IN"},
            {"guest_presenter", "Presentador invitado", "EVENT", "MANAGE_PRESENTATION"},
            {"survey_manager", "Encargado de encuesta", "EVENT", "MANAGE_SURVEY"}
        };
        final String sql = "INSERT OR IGNORE INTO roles "
                + "(uuid, key, name, description, scope, permissions, active, created_at, updated_at) "
                + "VALUES (?, ?, ?, NULL, ?, ?, 1, ?, ?)";
        try (var ps = stmt.getConnection().prepareStatement(sql)) {
            for (final Object[] r : roles) {
                final String now = java.time.Instant.now().toString();
                ps.setString(1, java.util.UUID.randomUUID().toString());
                ps.setString(2, (String) r[0]);
                ps.setString(3, (String) r[1]);
                ps.setString(4, (String) r[2]);
                ps.setString(5, (String) r[3]);
                ps.setString(6, now);
                ps.setString(7, now);
                ps.executeUpdate();
            }
        }
    }

    /**
     * Sembrado inicial del catálogo de tipos de evento: "conference" reune todas las capacidades
     * ya existentes en la plataforma (compatibilidad hacia atrás — toda conferencia existente
     * apunta a este key por default) y "workshop" ofrece el mismo conjunto salvo CODE_IDE, que
     * todavía no existe como capacidad (queda bloqueada hasta definir sus reglas de seguridad).
     */
    private void seedEventTypes(final Statement stmt) throws SQLException {
        final String allCapabilities = "TICKETING_GENERAL,TICKETING_SEATED,SURVEY,PRESENTATION,"
                + "WORD_CLOUD,CHAT_BOT,VIDEO_CONFERENCE,WHITEBOARD,DIAGRAMMING,COLLAB_NOTES,CODE_IDE";
        final Object[][] eventTypes = {
            {"conference", "Conferencia", "Evento con boletos, encuestas, presentación, nube de palabras, chat y videollamada.", allCapabilities},
            {"workshop", "Taller", "Evento colaborativo con boletos, encuestas, pizarra, diagramas y notas compartidas.", allCapabilities}
        };
        final String sql = "INSERT OR IGNORE INTO event_types "
                + "(uuid, key, name, description, capabilities, active, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, 1, ?, ?)";
        try (var ps = stmt.getConnection().prepareStatement(sql)) {
            for (final Object[] t : eventTypes) {
                final String now = java.time.Instant.now().toString();
                ps.setString(1, java.util.UUID.randomUUID().toString());
                ps.setString(2, (String) t[0]);
                ps.setString(3, (String) t[1]);
                ps.setString(4, (String) t[2]);
                ps.setString(5, (String) t[3]);
                ps.setString(6, now);
                ps.setString(7, now);
                ps.executeUpdate();
            }
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
