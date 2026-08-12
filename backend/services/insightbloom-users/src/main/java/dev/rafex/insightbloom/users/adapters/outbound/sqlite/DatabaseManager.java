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
            // Las migraciones de datos deben ejecutarse una sola vez. Sin esta tabla, una
            // reparación de compatibilidad podría volver a pisar una selección explícita del
            // organizador en cada reinicio del servicio.
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS schema_migrations (
                    version TEXT PRIMARY KEY,
                    applied_at TEXT NOT NULL
                )
            """);
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
            ColumnMigrationHelper.addColumnIfMissing(conn, "users", "public_profile_photo_base64", "TEXT");
            // Huella del dispositivo desde el que se creo la cuenta (inmutable, fijada una sola
            // vez al registrarse) -- ver PlatformDeviceGuard.checkRegistration, que cuenta cuantas
            // cuentas se crearon desde el mismo dispositivo en un dia para frenar spam de registro.
            ColumnMigrationHelper.addColumnIfMissing(conn, "users", "registration_device_fingerprint", "TEXT");
            // Metodo de acceso activo (PASSWORD por defecto) -- ver AuthMethod/SetAuthMethodUseCase.
            ColumnMigrationHelper.addColumnIfMissing(conn, "users", "auth_method", "TEXT NOT NULL DEFAULT 'PASSWORD'");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_users_email ON users(email)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_users_phone ON users(phone)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_users_registration_fingerprint "
                    + "ON users(registration_device_fingerprint)");

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
            // Huella de dispositivo capturada en el login (real o guest) -- ver PlatformDeviceGuard.
            // Como el token ya expira solo (1h usuario / 1h guest), contar "sesiones activas de
            // este fingerprint" contra esta tabla se auto-limpia sin necesidad de una tabla de
            // sesiones aparte.
            ColumnMigrationHelper.addColumnIfMissing(conn, "tokens", "device_fingerprint", "TEXT");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_tokens_device_fingerprint "
                    + "ON tokens(device_fingerprint)");
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
                    flyer_base64 TEXT,
                    description TEXT,
                    visibility TEXT NOT NULL DEFAULT 'PRIVATE',
                    schedule_markdown TEXT,
                    schedule_layout TEXT NOT NULL DEFAULT 'RIGHT',
                    public_theme TEXT NOT NULL DEFAULT 'CLASSIC',
                    ticket_price TEXT NOT NULL DEFAULT '0.00',
                    ticket_currency TEXT NOT NULL DEFAULT 'MXN',
                    ticket_sales_enabled INTEGER NOT NULL DEFAULT 1,
                    certificate_engine TEXT NOT NULL DEFAULT 'INHOUSE'
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
            try { stmt.executeUpdate("ALTER TABLE conferences ADD COLUMN description TEXT"); } catch (SQLException ignored) {}
            try { stmt.executeUpdate("ALTER TABLE conferences ADD COLUMN visibility TEXT NOT NULL DEFAULT 'PRIVATE'"); } catch (SQLException ignored) {}
            try { stmt.executeUpdate("ALTER TABLE conferences ADD COLUMN schedule_markdown TEXT"); } catch (SQLException ignored) {}
            try { stmt.executeUpdate("ALTER TABLE conferences ADD COLUMN schedule_layout TEXT NOT NULL DEFAULT 'RIGHT'"); } catch (SQLException ignored) {}
            try { stmt.executeUpdate("ALTER TABLE conferences ADD COLUMN public_theme TEXT NOT NULL DEFAULT 'CLASSIC'"); } catch (SQLException ignored) {}
            try { stmt.executeUpdate("ALTER TABLE conferences ADD COLUMN ticket_price TEXT NOT NULL DEFAULT '0.00'"); } catch (SQLException ignored) {}
            try { stmt.executeUpdate("ALTER TABLE conferences ADD COLUMN ticket_currency TEXT NOT NULL DEFAULT 'MXN'"); } catch (SQLException ignored) {}
            try { stmt.executeUpdate("ALTER TABLE conferences ADD COLUMN ticket_sales_enabled INTEGER NOT NULL DEFAULT 1"); } catch (SQLException ignored) {}
            try {
                stmt.executeUpdate("ALTER TABLE conferences ADD COLUMN certificate_engine TEXT NOT NULL DEFAULT 'INHOUSE'");
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
            try {
                stmt.executeUpdate("ALTER TABLE conferences ADD COLUMN sandbox_cli_lazyvim_pool_size INTEGER");
            } catch (SQLException ignored) {}
            try {
                stmt.executeUpdate("ALTER TABLE conferences ADD COLUMN max_devices_per_user INTEGER");
            } catch (SQLException ignored) {}
            try {
                stmt.executeUpdate("ALTER TABLE conferences ADD COLUMN max_accounts_per_device INTEGER");
            } catch (SQLException ignored) {}
            try {
                stmt.executeUpdate("ALTER TABLE conferences ADD COLUMN canvas_tool TEXT");
            } catch (SQLException ignored) {}
            try {
                stmt.executeUpdate("ALTER TABLE conferences ADD COLUMN canvas_audience_mode TEXT");
            } catch (SQLException ignored) {}
            try {
                stmt.executeUpdate("ALTER TABLE conferences ADD COLUMN on_demand_video_provider TEXT");
            } catch (SQLException ignored) {}
            try {
                stmt.executeUpdate("ALTER TABLE conferences ADD COLUMN on_demand_video_url TEXT");
            } catch (SQLException ignored) {}
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS conference_canvas_configs (
                    conference_uuid TEXT NOT NULL,
                    canvas_tool TEXT NOT NULL,
                    audience_mode TEXT NOT NULL,
                    PRIMARY KEY (conference_uuid, canvas_tool)
                )
            """);
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS conference_on_demand_cue_points (
                    conference_uuid TEXT NOT NULL,
                    at_seconds INTEGER NOT NULL,
                    label TEXT NOT NULL,
                    tool_path TEXT NOT NULL,
                    sort_order INTEGER NOT NULL
                )
            """);
            // Migra la configuración antigua de una sola herramienta sin duplicarla al
            // reiniciar. Las filas legacy sin herramienta concreta siguen representando el
            // comportamiento de todas las herramientas habilitadas por el tipo de evento.
            stmt.executeUpdate("""
                INSERT OR IGNORE INTO conference_canvas_configs (conference_uuid, canvas_tool, audience_mode)
                SELECT uuid, canvas_tool,
                       CASE WHEN canvas_tool = 'ETHERPAD'
                            THEN CASE WHEN canvas_audience_mode = 'INDEPENDENT' THEN 'INDEPENDENT' ELSE 'COLLABORATIVE' END
                            ELSE CASE WHEN canvas_audience_mode = 'MODERATOR_ONLY' THEN 'MODERATOR_ONLY' ELSE 'INDEPENDENT' END END
                FROM conferences
                WHERE canvas_tool IS NOT NULL AND TRIM(canvas_tool) <> ''
            """);
            // Corrige configuraciones antiguas que trataban Etherpad como si fuera un lienzo
            // publicable. Etherpad sólo tiene notas grupales o notas individuales.
            stmt.executeUpdate("""
                UPDATE conference_canvas_configs
                   SET audience_mode = CASE WHEN canvas_tool = 'ETHERPAD' THEN 'COLLABORATIVE' ELSE 'INDEPENDENT' END
                 WHERE (canvas_tool = 'ETHERPAD' AND audience_mode NOT IN ('COLLABORATIVE', 'INDEPENDENT'))
                    OR (canvas_tool <> 'ETHERPAD' AND audience_mode NOT IN ('INDEPENDENT', 'MODERATOR_ONLY'))
            """);
            stmt.executeUpdate("""
                UPDATE conferences
                   SET canvas_audience_mode = CASE WHEN canvas_tool = 'ETHERPAD' THEN 'COLLABORATIVE' ELSE 'INDEPENDENT' END
                 WHERE (canvas_tool = 'ETHERPAD' AND canvas_audience_mode NOT IN ('COLLABORATIVE', 'INDEPENDENT'))
                    OR (canvas_tool <> 'ETHERPAD' AND canvas_audience_mode NOT IN ('INDEPENDENT', 'MODERATOR_ONLY'))
            """);

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
                CREATE TABLE IF NOT EXISTS tickets (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    uuid TEXT NOT NULL UNIQUE,
                    conference_uuid TEXT NOT NULL,
                    ticket_code TEXT NOT NULL UNIQUE,
                    issued_by_user_uuid TEXT NOT NULL,
                    recipient_email TEXT,
                    seat_uuid TEXT,
                    operational INTEGER NOT NULL DEFAULT 0,
                    status TEXT NOT NULL,
                    claimed_by_user_uuid TEXT,
                    issued_at TEXT NOT NULL,
                    claimed_at TEXT,
                    checked_in_at TEXT,
                    revoked_by_user_uuid TEXT,
                    revoked_at TEXT
                )
            """);
            ColumnMigrationHelper.addColumnIfMissing(conn, "tickets", "revoked_by_user_uuid", "TEXT");
            ColumnMigrationHelper.addColumnIfMissing(conn, "tickets", "revoked_at", "TEXT");
            ColumnMigrationHelper.addColumnIfMissing(conn, "tickets", "operational", "INTEGER NOT NULL DEFAULT 0");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_tickets_conference ON tickets(conference_uuid)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_tickets_claimed_user ON tickets(conference_uuid, claimed_by_user_uuid)");
            // Migración idempotente de reservas históricas al modelo de boletos.
            stmt.executeUpdate("""
                INSERT OR IGNORE INTO tickets
                    (uuid, conference_uuid, ticket_code, issued_by_user_uuid, seat_uuid, status, claimed_by_user_uuid, issued_at)
                SELECT uuid, conference_uuid, ticket_code, user_uuid, seat_uuid, 'CLAIMED', user_uuid, created_at
                FROM reservations
            """);
            // El modelo legacy no tenía unicidad por usuario; conserva la más antigua antes de
            // instalar el índice que evita nuevas dobles emisiones concurrentes.
            stmt.executeUpdate("DELETE FROM reservations WHERE id NOT IN "
                    + "(SELECT MIN(id) FROM reservations GROUP BY conference_uuid, user_uuid)");
            stmt.executeUpdate("CREATE UNIQUE INDEX IF NOT EXISTS idx_reservations_conference_user "
                    + "ON reservations(conference_uuid, user_uuid)");

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
            // Limite de intentos de verificacion fallidos (login OTP): ver VerifyLoginOtpUseCase.
            ColumnMigrationHelper.addColumnIfMissing(conn, "otp_codes", "failed_attempts", "INTEGER NOT NULL DEFAULT 0");
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
                CREATE TABLE IF NOT EXISTS certificate_templates (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    conference_uuid TEXT NOT NULL UNIQUE,
                    template_key TEXT NOT NULL,
                    template_name TEXT NOT NULL,
                    engine TEXT NOT NULL DEFAULT 'HTML_CHROME',
                    document_json TEXT NOT NULL,
                    version INTEGER NOT NULL DEFAULT 1,
                    updated_by_user_uuid TEXT,
                    updated_at TEXT NOT NULL
                )
            """);
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_certificate_templates_conference ON certificate_templates(conference_uuid)");

            // Reparación única para bases creadas antes de que conferences.name fuera obligatorio.
            // El friendly_id es el identificador público estable y constituye un respaldo seguro
            // para que cualquier actualización posterior no falle por la restricción NOT NULL.
            final int conferenceNameMigration = stmt.executeUpdate("""
                INSERT OR IGNORE INTO schema_migrations(version, applied_at)
                VALUES ('conference-name-not-null-repair-v1', CURRENT_TIMESTAMP)
            """);
            if (conferenceNameMigration > 0) {
                stmt.executeUpdate("""
                    UPDATE conferences
                       SET name = friendly_id
                     WHERE name IS NULL OR TRIM(name) = ''
                """);
            }

            // Reparación única de datos creados por la primera versión del editor visual. Esa
            // versión guardaba certificate_templates.engine=HTML_CHROME, pero no sincronizaba
            // conferences.certificate_engine; la generación pública consulta la segunda columna
            // y terminaba usando PDFBox/Inhouse. Solo se corrigen filas donde la plantilla HTML
            // es más reciente que la conferencia, señal de ese estado inconsistente.
            final int certificateEngineMigration = stmt.executeUpdate("""
                INSERT OR IGNORE INTO schema_migrations(version, applied_at)
                VALUES ('certificate-engine-template-sync-v1', CURRENT_TIMESTAMP)
            """);
            if (certificateEngineMigration > 0) {
                stmt.executeUpdate("""
                    UPDATE conferences
                       SET certificate_engine = 'HTML_CHROME'
                     WHERE certificate_engine = 'INHOUSE'
                       AND EXISTS (
                           SELECT 1
                             FROM certificate_templates
                            WHERE certificate_templates.conference_uuid = conferences.uuid
                              AND certificate_templates.engine = 'HTML_CHROME'
                              AND certificate_templates.updated_at > conferences.updated_at
                       )
                """);
            }

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
                stmt.executeUpdate("ALTER TABLE conferences ADD COLUMN diagram_published_svg TEXT");
            } catch (SQLException ignored) {}
            try {
                stmt.executeUpdate("ALTER TABLE conferences ADD COLUMN diagram_updated_at TEXT");
            } catch (SQLException ignored) {}
            try {
                stmt.executeUpdate("ALTER TABLE conferences ADD COLUMN diagram_version INTEGER NOT NULL DEFAULT 0");
            } catch (SQLException ignored) {}
            try {
                stmt.executeUpdate("ALTER TABLE conferences ADD COLUMN diagram_purged_at TEXT");
            } catch (SQLException ignored) {}
            try {
                stmt.executeUpdate("ALTER TABLE conferences ADD COLUMN whiteboard_scene_json TEXT");
            } catch (SQLException ignored) {}
            try {
                stmt.executeUpdate("ALTER TABLE conferences ADD COLUMN whiteboard_published_svg TEXT");
            } catch (SQLException ignored) {}
            try {
                stmt.executeUpdate("ALTER TABLE conferences ADD COLUMN whiteboard_updated_at TEXT");
            } catch (SQLException ignored) {}
            try {
                stmt.executeUpdate("ALTER TABLE conferences ADD COLUMN whiteboard_version INTEGER NOT NULL DEFAULT 0");
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
            ColumnMigrationHelper.addColumnIfMissing(conn, "platform_settings", "ai_base_url", "TEXT");
            ColumnMigrationHelper.addColumnIfMissing(conn, "platform_settings", "ai_model", "TEXT");
            ColumnMigrationHelper.addColumnIfMissing(conn, "platform_settings", "ai_api_key_ciphertext", "TEXT");
            // Guardarails: capa de reglas de seguridad editable por el admin (anti-jailbreak, no
            // revelar el prompt/claves, no salirse del tema) que se concatena al prompt base y al
            // prompt propio de cada operacion antes de mandarlo al LLM. Ver GroqLlmClient.
            ColumnMigrationHelper.addColumnIfMissing(conn, "platform_settings", "chat_guardrails", "TEXT");
            for (final String capability : new String[] {"tutor", "survey", "seat_layout", "email"}) {
                ColumnMigrationHelper.addColumnIfMissing(conn, "platform_settings", capability + "_ai_configured", "INTEGER NOT NULL DEFAULT 0");
                ColumnMigrationHelper.addColumnIfMissing(conn, "platform_settings", capability + "_ai_enabled", "INTEGER NOT NULL DEFAULT 0");
                ColumnMigrationHelper.addColumnIfMissing(conn, "platform_settings", capability + "_ai_base_url", "TEXT");
                ColumnMigrationHelper.addColumnIfMissing(conn, "platform_settings", capability + "_ai_model", "TEXT");
                ColumnMigrationHelper.addColumnIfMissing(conn, "platform_settings", capability + "_ai_api_key_ciphertext", "TEXT");
                ColumnMigrationHelper.addColumnIfMissing(conn, "platform_settings", capability + "_ai_system_prompt", "TEXT");
                ColumnMigrationHelper.addColumnIfMissing(conn, "platform_settings", capability + "_ai_guardrails", "TEXT");
                ColumnMigrationHelper.addColumnIfMissing(conn, "platform_settings", capability + "_ai_temperature", "REAL");
            }
            // Umbrales de PlatformDeviceGuard (2026-07): nullable, defaults efectivos en el guard
            // (5/3/3) si el admin de plataforma no los configuro todavia.
            ColumnMigrationHelper.addColumnIfMissing(conn, "platform_settings", "max_accounts_per_device", "INTEGER");
            ColumnMigrationHelper.addColumnIfMissing(conn, "platform_settings", "max_sessions_per_user", "INTEGER");
            ColumnMigrationHelper.addColumnIfMissing(conn, "platform_settings",
                    "max_registrations_per_device_per_day", "INTEGER");

            // Control de egress por dominio (2026-07): lista blanca/negra GLOBAL que ve el
            // egress-proxy para IDE Web/CLI de TODOS los eventos -- ver ResolveEgressPolicyUseCase
            // y egress_policies (tabla de abajo) para la capa por evento que se suma encima.
            ColumnMigrationHelper.addColumnIfMissing(conn, "platform_settings", "egress_allowed_hosts", "TEXT");
            ColumnMigrationHelper.addColumnIfMissing(conn, "platform_settings", "egress_blocked_hosts", "TEXT");

            // Dispositivos bloqueados a nivel PLATAFORMA (no un evento puntual) por multicuenta o
            // spam de registro -- ver PlatformDeviceGuard. Un system_admin decide desbloquear
            // desde /dashboard/admin/device-access.
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS platform_device_blocks (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    uuid TEXT NOT NULL UNIQUE,
                    device_fingerprint TEXT NOT NULL,
                    reason TEXT NOT NULL,
                    related_count INTEGER NOT NULL,
                    blocked_at TEXT NOT NULL,
                    unblocked_at TEXT,
                    unblocked_by TEXT
                )
            """);
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_platform_device_blocks_fingerprint "
                    + "ON platform_device_blocks(device_fingerprint)");

            // Auditoria (no bloqueo) de discrepancias de fingerprint dentro de una MISMA sesion
            // ya logueada -- ver DeviceFingerprintAuditor. Una fila por sesion (token), no por
            // request, para no crecer sin control; occurrence_count se incrementa en cada
            // deteccion repetida de la misma sesion en vez de duplicar filas.
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS device_fingerprint_flags (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    uuid TEXT NOT NULL UNIQUE,
                    token_uuid TEXT NOT NULL UNIQUE,
                    subject_uuid TEXT NOT NULL,
                    subject_kind TEXT NOT NULL,
                    login_fingerprint TEXT NOT NULL,
                    last_seen_fingerprint TEXT NOT NULL,
                    occurrence_count INTEGER NOT NULL DEFAULT 1,
                    first_seen_at TEXT NOT NULL,
                    last_seen_at TEXT NOT NULL,
                    reviewed_at TEXT,
                    reviewed_by TEXT
                )
            """);
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_device_fingerprint_flags_subject "
                    + "ON device_fingerprint_flags(subject_uuid)");

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
            migrateReservationsUserUnique(conn);

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

            // Control de acceso por dispositivo (2026-07): cuántos dispositivos distintos usa un
            // mismo usuario en Jitsi/IDE dentro de un evento, y cuántas cuentas distintas comparten
            // un mismo dispositivo -- ver DeviceAccessGuard. Una fila = un dispositivo activo (o ya
            // revocado) de un usuario en una herramienta puntual dentro de una conferencia.
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS tool_device_sessions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    uuid TEXT NOT NULL UNIQUE,
                    conference_uuid TEXT NOT NULL,
                    user_uuid TEXT NOT NULL,
                    tool TEXT NOT NULL,
                    device_fingerprint TEXT NOT NULL,
                    first_seen_at TEXT NOT NULL,
                    last_seen_at TEXT NOT NULL,
                    revoked_at TEXT,
                    UNIQUE(conference_uuid, user_uuid, tool, device_fingerprint)
                )
            """);
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_tool_device_sessions_user "
                    + "ON tool_device_sessions(conference_uuid, user_uuid, tool)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_tool_device_sessions_device "
                    + "ON tool_device_sessions(conference_uuid, device_fingerprint)");

            // Local usage guard for the JaaS Free Developer tier. This is deliberately keyed by
            // InsightBloom user, not by browser/device: it is an operational estimate of unique
            // participants and avoids treating one attendee on two devices as two people.
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS jaas_monthly_participants (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    month TEXT NOT NULL,
                    user_uuid TEXT NOT NULL,
                    first_seen_at TEXT NOT NULL,
                    UNIQUE(month, user_uuid)
                )
            """);
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_jaas_monthly_participants_month "
                    + "ON jaas_monthly_participants(month)");

            // Dispositivos bloqueados por exceder max_accounts_per_device -- el moderador decide
            // desde el dashboard ("Bloqueos") si desbloquea (ver DEC de acceso por dispositivo).
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS device_blocks (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    uuid TEXT NOT NULL UNIQUE,
                    conference_uuid TEXT NOT NULL,
                    device_fingerprint TEXT NOT NULL,
                    account_count INTEGER NOT NULL,
                    blocked_at TEXT NOT NULL,
                    unblocked_at TEXT,
                    unblocked_by TEXT
                )
            """);
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_device_blocks_conference "
                    + "ON device_blocks(conference_uuid, device_fingerprint)");

            // Control de egress por dominio (2026-07): capa POR EVENTO que se suma a la global de
            // platform_settings -- ver EgressPolicy y ResolveEgressPolicyUseCase.
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS egress_policies (
                    conference_uuid TEXT PRIMARY KEY,
                    allowed_hosts TEXT,
                    blocked_hosts TEXT,
                    updated_at TEXT NOT NULL
                )
            """);

            // Publicacion publica de un backend/API REST vivo del sandbox (2026-07) -- a
            // diferencia del preview de workspace (ZIP estatico via insightbloom-presentations),
            // esto proxea un proceso vivo. UNIQUE(conference_uuid, user_uuid): un solo preview
            // activo por sandbox, publicar de nuevo reemplaza el anterior. Ver SandboxAppPreview.
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS sandbox_app_previews (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    uuid TEXT NOT NULL UNIQUE,
                    conference_uuid TEXT NOT NULL,
                    user_uuid TEXT NOT NULL,
                    pod_name TEXT NOT NULL,
                    target_port INTEGER NOT NULL,
                    access_token TEXT NOT NULL,
                    created_at TEXT NOT NULL,
                    expires_at TEXT NOT NULL,
                    UNIQUE(conference_uuid, user_uuid)
                )
            """);

            // Candado por herramienta (2026-07-27): ausencia de fila = bloqueado. Mismo patron
            // que survey_access_releases (insightbloom-survey) pero con tool_key extra --
            // user_uuid = '*' significa "liberado para todos". A proposito arranca vacia para
            // TODO evento, nuevo o ya corriendo: es la decision explicita del organizador de que
            // las 12 herramientas/acciones (Dudas, Temas, Presentacion, Chat, Video, Diagramas,
            // Pizarra, Notas, IDE y sus tres acciones de entrega) empiecen bloqueadas hasta que
            // el moderador las libere.
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS tool_access_releases (
                    conference_uuid TEXT NOT NULL,
                    tool_key TEXT NOT NULL,
                    user_uuid TEXT NOT NULL,
                    released_at TEXT NOT NULL,
                    PRIMARY KEY (conference_uuid, tool_key, user_uuid)
                )
            """);
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_tool_access_conference ON tool_access_releases(conference_uuid)");

            // Notificaciones dentro del portal (campana del header, 2026-08) -- ver
            // SendNotificationUseCase (inserta + empuja por SSE) y NotificationHandler.
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS notifications (
                    uuid TEXT PRIMARY KEY,
                    user_uuid TEXT NOT NULL,
                    type TEXT NOT NULL,
                    title TEXT NOT NULL,
                    body TEXT,
                    link_url TEXT,
                    created_at TEXT NOT NULL,
                    read_at TEXT
                )
            """);
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_notifications_user "
                    + "ON notifications(user_uuid, created_at DESC)");

            // Descarga async del workspace (2026-08) -- ver StartWorkspaceZipJobUseCase. El ZIP en
            // sí no se persiste acá (vive en memoria, ver WorkspaceZipCache); esta tabla solo
            // trackea el estado del job y el token de descarga con TTL de 2hs.
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS workspace_zip_jobs (
                    uuid TEXT PRIMARY KEY,
                    conference_uuid TEXT NOT NULL,
                    sandbox_uuid TEXT NOT NULL,
                    user_uuid TEXT NOT NULL,
                    status TEXT NOT NULL,
                    download_token TEXT,
                    created_at TEXT NOT NULL,
                    ready_at TEXT,
                    expires_at TEXT,
                    error_message TEXT
                )
            """);
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_workspace_zip_jobs_expires "
                    + "ON workspace_zip_jobs(expires_at)");

            backfillMissingTicketsForSimpleJoins(conn);
            backfillMissingCapacity(conn);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize database", e);
        }
    }

    /**
     * Todo evento declara un aforo, sin excepción para eventos NONE/virtuales (DEC 2026-07-18):
     * la infraestructura tiene recursos finitos. Para conferencias que nunca tuvieron aforo
     * (capacity IS NULL -- el caso de casi todas antes de este cambio), primero reconcilia
     * reserved_count desde los boletos reales (el contador incremental nunca se usó fuera de
     * GENERAL) y después fija capacity al mayor entre el default (10) y la gente ya inscripta,
     * para no dejar eventos existentes por encima de su propio aforo apenas arranca el server.
     * Idempotente -- una vez que capacity queda definido, no se vuelve a tocar.
     */
    private void backfillMissingCapacity(final Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("""
                UPDATE conferences
                SET reserved_count = (SELECT COUNT(*) FROM reservations r WHERE r.conference_uuid = conferences.uuid)
                WHERE capacity IS NULL
            """);
            stmt.executeUpdate("""
                UPDATE conferences
                SET capacity = MAX(10, reserved_count)
                WHERE capacity IS NULL
            """);
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
     * Un usuario no puede tener mas de un boleto por conferencia (GENERAL o SEATED) -- antes solo
     * el uniqueness de asiento (UNIQUE(conference_uuid, seat_uuid)) protegia SEATED, dejando
     * GENERAL/NONE (seat_uuid null) vulnerable a una condicion de carrera de "doble click" que
     * genera dos boletos y descuenta el aforo dos veces (ver auditoria de seguridad 2026-07).
     * Antes de recrear la tabla con el UNIQUE nuevo, se depuran duplicados existentes (se queda
     * con el boleto mas antiguo por conference_uuid+user_uuid) para que el INSERT...SELECT no
     * falle contra el propio constraint que se esta agregando.
     */
    private void migrateReservationsUserUnique(final Connection conn) throws SQLException {
        String createSql = null;
        try (Statement s = conn.createStatement();
             ResultSet rs = s.executeQuery(
                 "SELECT sql FROM sqlite_master WHERE type='table' AND name='reservations'")) {
            if (rs.next()) createSql = rs.getString("sql");
        }
        final boolean alreadyMigrated = createSql != null
                && createSql.replaceAll("\\s+", " ").contains("UNIQUE(conference_uuid, user_uuid)");
        if (alreadyMigrated) {
            return;
        }
        try (Statement s = conn.createStatement()) {
            s.executeUpdate("""
                DELETE FROM reservations
                WHERE id NOT IN (
                    SELECT MIN(id) FROM reservations GROUP BY conference_uuid, user_uuid
                )
            """);
            s.executeUpdate("""
                CREATE TABLE reservations_new (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    uuid TEXT NOT NULL UNIQUE,
                    conference_uuid TEXT NOT NULL,
                    user_uuid TEXT NOT NULL,
                    seat_uuid TEXT,
                    ticket_code TEXT NOT NULL UNIQUE,
                    status TEXT NOT NULL,
                    created_at TEXT NOT NULL,
                    checked_in_at TEXT,
                    UNIQUE(conference_uuid, seat_uuid),
                    UNIQUE(conference_uuid, user_uuid)
                )
            """);
            s.executeUpdate("""
                INSERT INTO reservations_new
                    (id, uuid, conference_uuid, user_uuid, seat_uuid, ticket_code, status, created_at, checked_in_at)
                SELECT id, uuid, conference_uuid, user_uuid, seat_uuid, ticket_code, status, created_at, checked_in_at
                FROM reservations
            """);
            s.executeUpdate("DROP TABLE reservations");
            s.executeUpdate("ALTER TABLE reservations_new RENAME TO reservations");
            s.executeUpdate("CREATE INDEX IF NOT EXISTS idx_reservations_conference ON reservations(conference_uuid)");
            s.executeUpdate("CREATE INDEX IF NOT EXISTS idx_reservations_user ON reservations(user_uuid)");
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
                "MANAGE_EVENT_SETTINGS,ASSIGN_EVENT_ROLES,MANAGE_TICKETS,MODERATE_CONTENT,CHECK_IN,MANAGE_PRESENTATION,MANAGE_SURVEY,MANAGE_CERTIFICATE,VIDEO_MODERATE"},
            {"moderator", "Moderador", "EVENT", "MANAGE_TICKETS,MODERATE_CONTENT,MANAGE_SURVEY,MANAGE_CERTIFICATE,VIDEO_MODERATE"},
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
        // Agrega el permiso a instalaciones existentes sin duplicarlo.
        stmt.executeUpdate("UPDATE roles SET permissions = permissions || ',MANAGE_TICKETS' "
                + "WHERE key IN ('host','moderator') AND instr(',' || permissions || ',', ',MANAGE_TICKETS,') = 0");
        stmt.executeUpdate("UPDATE roles SET permissions = permissions || ',MANAGE_CERTIFICATE' "
                + "WHERE key IN ('host','moderator') AND instr(',' || permissions || ',', ',MANAGE_CERTIFICATE,') = 0");
        stmt.executeUpdate("UPDATE roles SET permissions = permissions || ',MANAGE_SURVEY' "
                + "WHERE key = 'moderator' AND instr(',' || permissions || ',', ',MANAGE_SURVEY,') = 0");
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
