package dev.rafex.insightbloom.users.adapters.outbound.sqlite;

import dev.rafex.insightbloom.users.domain.model.OtpChannel;
import dev.rafex.insightbloom.users.domain.model.OtpCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteOtpCodeRepositoryDeliveryTest {
    @Test
    void migrationMarksPreexistingCodesAsDelivered(@TempDir final Path tempDir) throws Exception {
        final String databasePath = tempDir.resolve("legacy-users.db").toString();
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
             var statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE otp_codes (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        uuid TEXT NOT NULL UNIQUE,
                        identifier TEXT NOT NULL,
                        channel TEXT NOT NULL,
                        code TEXT NOT NULL,
                        expires_at TEXT NOT NULL,
                        consumed INTEGER NOT NULL DEFAULT 0,
                        created_at TEXT NOT NULL,
                        failed_attempts INTEGER NOT NULL DEFAULT 0
                    )
                    """);
            statement.executeUpdate("""
                    INSERT INTO otp_codes (uuid, identifier, channel, code, expires_at, consumed, created_at)
                    VALUES ('legacy', 'legacy@example.test', 'EMAIL', '654321', '2026-09-23T16:00:00Z', 0,
                            '2026-09-23T15:00:00Z')
                    """);
        }

        final DatabaseManager db = new DatabaseManager(databasePath);
        db.initialize();
        final var repo = new SqliteOtpCodeRepository(db);

        assertTrue(repo.findLatestActive("legacy@example.test").isPresent());
        assertEquals(1, repo.countSince("legacy@example.test", Instant.parse("2026-09-23T14:00:00Z")));
    }

    @Test
    void pendingCodeIsNeitherVerifiableNorCountedUntilDeliveryAccepted(@TempDir final Path tempDir) {
        final DatabaseManager db = new DatabaseManager(tempDir.resolve("users.db").toString());
        db.initialize();
        final var repo = new SqliteOtpCodeRepository(db);
        final var code = new OtpCode("person@example.test", OtpChannel.EMAIL, "123456", Instant.now().plusSeconds(600));

        repo.savePending(code);

        assertTrue(repo.findLatestActive("person@example.test").isEmpty());
        assertEquals(0, repo.countSince("person@example.test", Instant.now().minusSeconds(60)));

        repo.markDelivered(code.getUuid());

        assertTrue(repo.findLatestActive("person@example.test").isPresent());
        assertEquals(1, repo.countSince("person@example.test", Instant.now().minusSeconds(60)));
    }
}
