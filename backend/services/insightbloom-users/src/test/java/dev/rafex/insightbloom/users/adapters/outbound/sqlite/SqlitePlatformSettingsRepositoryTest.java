package dev.rafex.insightbloom.users.adapters.outbound.sqlite;

import dev.rafex.insightbloom.users.domain.model.PlatformSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Integration test contra SQLite real (no un fake en memoria) -- a proposito, porque un bug real
 * en produccion (2026-07-26) fue justamente que las columnas egress_allowed_hosts/blocked_hosts
 * se agregaron al SQL de save() pero se olvidaron los ps.setString(...) correspondientes: el
 * driver bindeaba NULL en silencio, sin excepcion, y ningun test con un repositorio fake lo
 * hubiera detectado.
 */
class SqlitePlatformSettingsRepositoryTest {

    private SqlitePlatformSettingsRepository repositoryFor(final Path tempDir) {
        final DatabaseManager database = new DatabaseManager(tempDir.resolve("users.db").toString());
        database.initialize();
        return new SqlitePlatformSettingsRepository(database);
    }

    @Test
    void roundTripsEgressPolicyFields(@TempDir final Path tempDir) {
        final SqlitePlatformSettingsRepository repository = repositoryFor(tempDir);
        final PlatformSettings settings = repository.get();
        settings.setEgressAllowedHosts("github.com,*.npmjs.org");
        settings.setEgressBlockedHosts("localhost,169.254.169.254");

        repository.save(settings);

        final PlatformSettings restored = repository.get();
        assertEquals("github.com,*.npmjs.org", restored.getEgressAllowedHosts());
        assertEquals("localhost,169.254.169.254", restored.getEgressBlockedHosts());
    }

    @Test
    void roundTripsEgressPolicyFieldsAcrossRepeatedSaves(@TempDir final Path tempDir) {
        final SqlitePlatformSettingsRepository repository = repositoryFor(tempDir);
        final PlatformSettings first = repository.get();
        first.setEgressAllowedHosts("github.com");
        first.setEgressBlockedHosts("localhost");
        repository.save(first);

        // Un segundo save (ej. desde un flujo no relacionado, como AI settings) no debe perder
        // los valores de egress ya guardados -- confirma el patron read-modify-write.
        final PlatformSettings second = repository.get();
        second.setMaxAccountsPerDevice(7);
        repository.save(second);

        final PlatformSettings restored = repository.get();
        assertEquals("github.com", restored.getEgressAllowedHosts());
        assertEquals("localhost", restored.getEgressBlockedHosts());
        assertEquals(7, restored.getMaxAccountsPerDevice());
    }

    @Test
    void globalAiCredentialsAreNeitherReturnedNorPersisted(@TempDir final Path tempDir) throws Exception {
        final Path path = tempDir.resolve("users.db");
        final DatabaseManager database = new DatabaseManager(path.toString());
        database.initialize();
        final SqlitePlatformSettingsRepository repository = new SqlitePlatformSettingsRepository(database);
        final PlatformSettings settings = repository.get();
        assertFalse(settings.isChatAiEnabled());
        assertNull(settings.getAiApiKey());
        assertNull(settings.getAiBaseUrl());

        settings.setChatAiEnabled(true);
        settings.setAiApiKey("must-not-persist");
        repository.save(settings);

        final PlatformSettings restored = repository.get();
        assertFalse(restored.isChatAiEnabled());
        assertNull(restored.getAiApiKey());
        try (var connection = java.sql.DriverManager.getConnection("jdbc:sqlite:" + path);
             var statement = connection.createStatement();
             var row = statement.executeQuery("SELECT chat_ai_enabled, ai_api_key_ciphertext FROM platform_settings WHERE id=1")) {
            row.next();
            assertEquals(0, row.getInt(1));
            assertNull(row.getString(2));
        }
    }
}
