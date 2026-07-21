package dev.rafex.insightbloom.survey.adapters.outbound.sqlite;

import dev.rafex.insightbloom.common.migration.ColumnMigrationHelper;
import dev.rafex.insightbloom.common.sqlite.SqliteConnectionProvider;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseManager {
    private final SqliteConnectionProvider provider;

    public DatabaseManager(final String dbPath) { this.provider = new SqliteConnectionProvider(dbPath); }

    public Connection getConnection() throws SQLException {
        return provider.getConnection();
    }

    public void initialize() {
        try (Connection c = getConnection(); Statement stmt = c.createStatement()) {
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS survey_questions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    uuid TEXT NOT NULL UNIQUE,
                    conference_uuid TEXT NOT NULL,
                    text TEXT NOT NULL,
                    type TEXT NOT NULL,
                    options_json TEXT,
                    order_index INTEGER NOT NULL DEFAULT 0,
                    active INTEGER NOT NULL DEFAULT 1,
                    created_at TEXT NOT NULL,
                    updated_at TEXT NOT NULL
                )""");
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS survey_responses (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    uuid TEXT NOT NULL UNIQUE,
                    conference_uuid TEXT NOT NULL,
                    question_uuid TEXT NOT NULL,
                    respondent_token TEXT NOT NULL,
                    answer_text TEXT,
                    answer_rating INTEGER,
                    submitted_at TEXT NOT NULL
                )""");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_survey_q_conf ON survey_questions(conference_uuid)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_survey_r_conf ON survey_responses(conference_uuid)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_survey_r_q ON survey_responses(question_uuid)");

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS survey_definitions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    uuid TEXT NOT NULL UNIQUE,
                    conference_uuid TEXT NOT NULL UNIQUE,
                    engine TEXT NOT NULL,
                    schema_json TEXT NOT NULL,
                    schema_version INTEGER NOT NULL DEFAULT 1,
                    status TEXT NOT NULL DEFAULT 'DRAFT',
                    created_at TEXT NOT NULL,
                    updated_at TEXT NOT NULL,
                    published_at TEXT
                )""");
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS survey_submissions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    uuid TEXT NOT NULL UNIQUE,
                    conference_uuid TEXT NOT NULL,
                    definition_uuid TEXT NOT NULL,
                    definition_version INTEGER NOT NULL,
                    user_uuid TEXT NOT NULL,
                    payload_json TEXT NOT NULL,
                    submitted_at TEXT NOT NULL,
                    UNIQUE(conference_uuid, user_uuid)
                )""");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_survey_def_conf ON survey_definitions(conference_uuid)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_survey_sub_conf ON survey_submissions(conference_uuid)");

            ColumnMigrationHelper.addColumnIfMissing(c, "survey_questions", "reference_answer", "TEXT");
            ColumnMigrationHelper.addColumnIfMissing(c, "survey_questions", "rating_style", "TEXT");
            ColumnMigrationHelper.addColumnIfMissing(c, "survey_responses", "grade_score", "REAL");
            ColumnMigrationHelper.addColumnIfMissing(c, "survey_responses", "grade_feedback", "TEXT");
            ColumnMigrationHelper.addColumnIfMissing(c, "survey_responses", "user_uuid", "TEXT");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_survey_r_user ON survey_responses(conference_uuid, user_uuid)");
            // Default 1 (obligatoria): preguntas ya creadas antes de este campo se
            // comportan igual que hoy (se espera que se respondan todas).
            ColumnMigrationHelper.addColumnIfMissing(c, "survey_questions", "required", "INTEGER NOT NULL DEFAULT 1");
        } catch (final SQLException e) {
            throw new RuntimeException("Failed to init survey db", e);
        }
    }
}
