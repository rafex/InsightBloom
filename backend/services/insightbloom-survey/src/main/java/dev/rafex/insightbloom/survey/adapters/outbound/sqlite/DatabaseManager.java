package dev.rafex.insightbloom.survey.adapters.outbound.sqlite;

import org.sqlite.SQLiteConfig;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

public class DatabaseManager {
    private final String dbPath;

    public DatabaseManager(final String dbPath) { this.dbPath = dbPath; }

    public Connection getConnection() throws SQLException {
        final SQLiteConfig config = new SQLiteConfig();
        config.setJournalMode(SQLiteConfig.JournalMode.WAL);
        config.setBusyTimeout(5000);
        return DriverManager.getConnection("jdbc:sqlite:" + dbPath, config.toProperties());
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

            addColumnIfMissing(c, "survey_questions", "reference_answer", "TEXT");
            addColumnIfMissing(c, "survey_questions", "rating_style", "TEXT");
            addColumnIfMissing(c, "survey_responses", "grade_score", "REAL");
            addColumnIfMissing(c, "survey_responses", "grade_feedback", "TEXT");
        } catch (final SQLException e) {
            throw new RuntimeException("Failed to init survey db", e);
        }
    }

    private void addColumnIfMissing(final Connection c, final String table, final String column,
                                     final String ddlType) throws SQLException {
        final Set<String> existing = new HashSet<>();
        try (Statement stmt = c.createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) existing.add(rs.getString("name"));
        }
        if (!existing.contains(column)) {
            try (Statement stmt = c.createStatement()) {
                stmt.executeUpdate("ALTER TABLE " + table + " ADD COLUMN " + column + " " + ddlType);
            }
        }
    }
}
