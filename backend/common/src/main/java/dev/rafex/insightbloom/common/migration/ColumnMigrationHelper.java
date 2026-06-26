package dev.rafex.insightbloom.common.migration;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

public final class ColumnMigrationHelper {

    private ColumnMigrationHelper() {
    }

    public static void addColumnIfMissing(final Connection c, final String table, final String column,
                                          final String ddlType) throws SQLException {
        final Set<String> existing = new HashSet<>();
        try (Statement stmt = c.createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                existing.add(rs.getString("name"));
            }
        }
        if (!existing.contains(column)) {
            try (Statement stmt = c.createStatement()) {
                stmt.executeUpdate("ALTER TABLE " + table + " ADD COLUMN " + column + " " + ddlType);
            }
        }
    }
}
