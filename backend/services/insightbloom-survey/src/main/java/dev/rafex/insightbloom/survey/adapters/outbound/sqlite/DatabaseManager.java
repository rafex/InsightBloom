package dev.rafex.insightbloom.survey.adapters.outbound.sqlite;

import org.sqlite.SQLiteConfig;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

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
        } catch (final SQLException e) {
            throw new RuntimeException("Failed to init survey db", e);
        }
    }
}
