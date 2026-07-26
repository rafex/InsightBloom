package dev.rafex.insightbloom.users.adapters.outbound.sqlite;

import dev.rafex.insightbloom.users.adapters.outbound.crypto.AiApiKeyCipher;
import dev.rafex.insightbloom.users.domain.model.PlatformSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
        return new SqlitePlatformSettingsRepository(database, new AiApiKeyCipher("test-encryption-key-32-bytes!!!!"));
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
}
