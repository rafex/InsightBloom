package dev.rafex.insightbloom.users.adapters.outbound.sqlite;

import dev.rafex.insightbloom.users.domain.model.ToolDeviceSession;
import dev.rafex.insightbloom.users.domain.model.ToolKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteToolDeviceSessionRepositoryTest {

    @Test
    void reactivatesRevokedIdentityAfterVideoTakeover(@TempDir final Path tempDir) {
        final DatabaseManager database = new DatabaseManager(tempDir.resolve("users.db").toString());
        database.initialize();
        final SqliteToolDeviceSessionRepository repository = new SqliteToolDeviceSessionRepository(database);

        final ToolDeviceSession first = new ToolDeviceSession("conference-1", "user-1", ToolKind.VIDEO, "device-1");
        repository.save(first);
        repository.revoke(first.getUuid());

        final ToolDeviceSession reopened = new ToolDeviceSession("conference-1", "user-1", ToolKind.VIDEO, "device-1");
        repository.save(reopened);

        final ToolDeviceSession active = repository
                .findActive("conference-1", "user-1", ToolKind.VIDEO, "device-1")
                .orElseThrow();
        assertEquals(reopened.getUuid(), active.getUuid());
        assertTrue(active.isActive());
    }
}
