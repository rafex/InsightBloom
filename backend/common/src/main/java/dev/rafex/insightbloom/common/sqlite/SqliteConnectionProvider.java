package dev.rafex.insightbloom.common.sqlite;

import org.sqlite.SQLiteConfig;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class SqliteConnectionProvider {

    private final String dbPath;

    public SqliteConnectionProvider(String dbPath) {
        this.dbPath = dbPath;
    }

    public String dbPath() {
        return dbPath;
    }

    public Connection getConnection() throws SQLException {
        final SQLiteConfig config = new SQLiteConfig();
        config.setJournalMode(SQLiteConfig.JournalMode.WAL);
        config.setBusyTimeout(5000);
        return DriverManager.getConnection("jdbc:sqlite:" + dbPath, config.toProperties());
    }
}
