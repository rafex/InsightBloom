package dev.rafex.insightbloom.users.adapters.outbound.sqlite;

import dev.rafex.insightbloom.users.domain.model.Conference;
import dev.rafex.insightbloom.users.domain.model.CertificateTemplate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.time.Instant;

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
