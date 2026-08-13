package dev.rafex.insightbloom.users.adapters.outbound.sqlite;

import dev.rafex.insightbloom.users.domain.model.CanvasAudienceMode;
import dev.rafex.insightbloom.users.domain.model.CanvasTool;
import dev.rafex.insightbloom.users.domain.model.Conference;
import dev.rafex.insightbloom.users.domain.model.CertificateTemplate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void preservesModeratorOnlyModeForEtherpad(@TempDir final Path tempDir) {
        final DatabaseManager database = new DatabaseManager(tempDir.resolve("users.db").toString());
        database.initialize();
        final SqliteConferenceRepository repository = new SqliteConferenceRepository(database);
        final Conference conference = new Conference("etherpad-test", "Etherpad test", "owner");
        conference.setCanvasTool(CanvasTool.ETHERPAD);
        conference.setCanvasAudienceMode(CanvasAudienceMode.MODERATOR_ONLY);

        repository.save(conference);

        final Conference restored = repository.findByUuid(conference.getUuid()).orElseThrow();
        assertEquals(CanvasAudienceMode.MODERATOR_ONLY, restored.getCanvasAudienceMode());
        assertEquals(CanvasAudienceMode.MODERATOR_ONLY, restored.getCanvasConfigs().getFirst().audienceMode());
    }

    @Test
    void persistsCertificateEngine(@TempDir final Path tempDir) {
        final DatabaseManager database = new DatabaseManager(tempDir.resolve("users.db").toString());
        database.initialize();
        final SqliteConferenceRepository repository = new SqliteConferenceRepository(database);
        final Conference conference = new Conference("certificate-test", "Certificate test", "owner");
        conference.setCertificateEngine("HTML_CHROME");

        repository.save(conference);

        final Conference restored = repository.findByUuid(conference.getUuid()).orElseThrow();
        assertEquals("HTML_CHROME", restored.getCertificateEngine());
    }

    @Test
    void usesFriendlyIdWhenLegacyConferenceHasNoName(@TempDir final Path tempDir) {
        final DatabaseManager database = new DatabaseManager(tempDir.resolve("users.db").toString());
        database.initialize();
        final SqliteConferenceRepository repository = new SqliteConferenceRepository(database);
        final Conference conference = new Conference("legacy-event", null, "owner");

        repository.save(conference);

        assertEquals("legacy-event", repository.findByUuid(conference.getUuid()).orElseThrow().getName());
    }

    @Test
    void persistsPublicBoardThemeAndDefaultsUnknownValuesToClassic(@TempDir final Path tempDir) {
        final DatabaseManager database = new DatabaseManager(tempDir.resolve("users.db").toString());
        database.initialize();
        final SqliteConferenceRepository repository = new SqliteConferenceRepository(database);
        final Conference conference = new Conference("public-theme-test", "Public theme test", "owner");
        conference.setPublicTheme("EDITORIAL");
        repository.save(conference);

        final Conference restored = repository.findByUuid(conference.getUuid()).orElseThrow();
        assertEquals("EDITORIAL", restored.getPublicTheme());

        restored.setPublicTheme("not-a-theme");
        assertEquals("CLASSIC", restored.getPublicTheme());
    }

    @Test
    void listsOwnedAndAssignedConferencesForTheDashboard(@TempDir final Path tempDir) throws Exception {
        final DatabaseManager database = new DatabaseManager(tempDir.resolve("users.db").toString());
        database.initialize();
        final SqliteConferenceRepository repository = new SqliteConferenceRepository(database);
        final Conference owned = new Conference("owned-event", "Owned event", "user-1");
        final Conference assigned = new Conference("assigned-event", "Assigned event", "user-2");
        final Conference unrelated = new Conference("unrelated-event", "Unrelated event", "user-3");
        repository.save(owned);
        repository.save(assigned);
        repository.save(unrelated);

        try (Connection connection = database.getConnection(); var statement = connection.prepareStatement(
                "INSERT INTO event_roles (uuid, event_uuid, user_uuid, role_key, assigned_at) VALUES (?, ?, ?, ?, ?)")) {
            statement.setString(1, "role-assignment-1");
            statement.setString(2, assigned.getUuid());
            statement.setString(3, "user-1");
            statement.setString(4, "moderator");
            statement.setString(5, Instant.now().toString());
            statement.executeUpdate();
        }

        final List<Conference> visible = repository.findByUser("user-1");

        final List<String> visibleIds = visible.stream().map(Conference::getUuid).toList();
        assertEquals(2, visibleIds.size());
        assertTrue(visibleIds.contains(owned.getUuid()));
        assertTrue(visibleIds.contains(assigned.getUuid()));
        assertTrue(!visibleIds.contains(unrelated.getUuid()));
    }

    @Test
    void repairsLegacyEditorEngineMismatchOnce(@TempDir final Path tempDir) throws Exception {
        final DatabaseManager database = new DatabaseManager(tempDir.resolve("users.db").toString());
        database.initialize();
        final SqliteConferenceRepository conferences = new SqliteConferenceRepository(database);
        final SqliteCertificateTemplateRepository templates = new SqliteCertificateTemplateRepository(database);
        final Conference conference = new Conference("certificate-migration-test", "Certificate migration test", "owner");
        conferences.save(conference);
        templates.save(new CertificateTemplate(conference.getUuid(), "classic", "Clásico", "HTML_CHROME",
                "{\"page\":{},\"blocks\":[]}", 1, "owner", Instant.now().plusSeconds(1)));

        // Simula una base creada antes de registrar esta migración.
        try (Connection connection = database.getConnection(); var statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM schema_migrations WHERE version = 'certificate-engine-template-sync-v1'");
        }
        database.initialize();

        assertEquals("HTML_CHROME", conferences.findByUuid(conference.getUuid()).orElseThrow().getCertificateEngine());
    }
}
