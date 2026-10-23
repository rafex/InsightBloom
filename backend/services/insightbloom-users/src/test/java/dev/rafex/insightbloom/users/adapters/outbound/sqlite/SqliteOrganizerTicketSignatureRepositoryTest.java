package dev.rafex.insightbloom.users.adapters.outbound.sqlite;

import dev.rafex.insightbloom.users.domain.ports.OrganizerTicketSignatureRepository.OfflinePackage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

class SqliteOrganizerTicketSignatureRepositoryTest {
    @Test
    void invalidatingPackageStopsDownloadsButKeepsCurrentReaderPackageAvailableForSync(@TempDir Path tempDir) {
        final var db = new DatabaseManager(tempDir.resolve("users.db").toString()); db.initialize();
        final var repo = new SqliteOrganizerTicketSignatureRepository(db);
        final Instant now = Instant.now();
        repo.registerKey("event-1", "key-1", "cHVibGlj", "organizer", now);
        final var first = new OfflinePackage("event-1", "key-1", "manifest-1", "cose-1", now, now.plusSeconds(3600));
        repo.saveOfflinePackage(first, "organizer");

        assertEquals(first, repo.offlinePackage("event-1").orElseThrow());
        repo.invalidateOfflinePackage("event-1");
        assertTrue(repo.offlinePackage("event-1").isEmpty());
        assertTrue(repo.isKnownOfflinePackage("event-1", "manifest-1", "cose-1", now));

        final var second = new OfflinePackage("event-1", "key-1", "manifest-2", "cose-2", now.plusSeconds(5), now.plusSeconds(3605));
        repo.saveOfflinePackage(second, "organizer");
        assertEquals(second, repo.offlinePackage("event-1").orElseThrow());
        assertTrue(repo.isKnownOfflinePackage("event-1", "manifest-1", "cose-1", now));
    }
}
