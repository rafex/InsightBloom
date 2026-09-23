package dev.rafex.insightbloom.users.adapters.outbound.sqlite;

import dev.rafex.insightbloom.users.domain.model.OtpRequestAudit;
import dev.rafex.insightbloom.users.domain.ports.OtpRequestAuditPort.Filter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SqliteOtpRequestAuditRepositoryTest {
    @Test
    void storesAndFiltersAuditWithoutSubmittedIdentifier(@TempDir final Path tempDir) {
        final DatabaseManager db = new DatabaseManager(tempDir.resolve("users.db").toString());
        db.initialize();
        final SqliteOtpRequestAuditRepository repo = new SqliteOtpRequestAuditRepository(db);
        final Instant now = Instant.parse("2026-09-23T12:00:00Z");
        repo.save(new OtpRequestAudit(now, "user-1", "198.51.100.7", "browser", "smtp_accepted"));
        repo.save(new OtpRequestAudit(now.plusSeconds(1), "user-1", "198.51.100.7", "browser", "smtp_failed"));
        repo.save(new OtpRequestAudit(now.plusSeconds(2), null, "198.51.100.8", "browser", "account_not_found"));

        final var page = repo.find(new Filter(now, now.plusSeconds(5), null, "198.51.100.7", null), 10, 0);
        assertEquals(2, page.total());
        assertEquals(2, page.items().size());
        assertEquals("smtp_failed", page.items().getFirst().outcome());
    }

    @Test
    void deletesExpiredRowsUsingThirtyDayCutoff(@TempDir final Path tempDir) {
        final DatabaseManager db = new DatabaseManager(tempDir.resolve("users.db").toString());
        db.initialize();
        final SqliteOtpRequestAuditRepository repo = new SqliteOtpRequestAuditRepository(db);
        final Instant cutoff = Instant.parse("2026-09-23T12:00:00Z");
        repo.save(new OtpRequestAudit(cutoff.minusSeconds(1), null, "198.51.100.7", null, "account_not_found"));
        repo.save(new OtpRequestAudit(cutoff.plusSeconds(1), null, "198.51.100.7", null, "account_not_found"));

        assertEquals(1, repo.deleteOlderThan(cutoff));
        assertEquals(1, repo.find(new Filter(null, null, null, null, null), 10, 0).total());
    }
}
