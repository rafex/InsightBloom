package dev.rafex.insightbloom.users.adapters.outbound.sqlite;

import dev.rafex.insightbloom.users.domain.model.Conference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SqliteConferenceRepositoryTest {
    @Test
    void persistsWhiteboardSceneAndPublication(@TempDir final Path tempDir) {
        final DatabaseManager database = new DatabaseManager(tempDir.resolve("users.db").toString());
        database.initialize();
        final SqliteConferenceRepository repository = new SqliteConferenceRepository(database);
        final Conference conference = new Conference("whiteboard-test", "Whiteboard test", "moderator-1");
        conference.setWhiteboardSceneAndPublishedSvg("{\"elements\":[]}", "data:image/svg+xml;base64,AAA");

        repository.save(conference);

        final Conference restored = repository.findByUuid(conference.getUuid()).orElseThrow();
        assertEquals("{\"elements\":[]}", restored.getWhiteboardSceneJson());
        assertEquals("data:image/svg+xml;base64,AAA", restored.getWhiteboardPublishedSvg());
        assertEquals(1, restored.getWhiteboardVersion());
        assertNotNull(restored.getWhiteboardUpdatedAt());
    }

    @Test
    void normalizesLegacyModeratorOnlyEtherpadToCollaborative(@TempDir final Path tempDir) {
        final DatabaseManager database = new DatabaseManager(tempDir.resolve("users.db").toString());
        database.initialize();
        final SqliteConferenceRepository repository = new SqliteConferenceRepository(database);
        final Conference conference = new Conference("etherpad-test", "Etherpad test", "owner");
        conference.setCanvasTool("ETHERPAD");
        conference.setCanvasAudienceMode("MODERATOR_ONLY");

        repository.save(conference);

        final Conference restored = repository.findByUuid(conference.getUuid()).orElseThrow();
        assertEquals("COLLABORATIVE", restored.getCanvasAudienceMode());
        assertEquals("COLLABORATIVE", restored.getCanvasConfigs().getFirst().audienceMode());
    }
}
