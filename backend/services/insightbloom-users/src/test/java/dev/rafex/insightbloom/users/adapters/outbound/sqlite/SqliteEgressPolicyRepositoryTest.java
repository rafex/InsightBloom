package dev.rafex.insightbloom.users.adapters.outbound.sqlite;

import dev.rafex.insightbloom.users.domain.model.EgressPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteEgressPolicyRepositoryTest {

    @Test
    void persistsAllowAllWithADenyByDefaultSchema(@TempDir final Path tempDir) throws Exception {
        final DatabaseManager database = new DatabaseManager(tempDir.resolve("users.db").toString());
        database.initialize();
        final SqliteEgressPolicyRepository repository = new SqliteEgressPolicyRepository(database);

        repository.save(new EgressPolicy("conf-1", null, null, true, Instant.now()));
        assertTrue(repository.findByConference("conf-1").orElseThrow().allowAll());

        try (var connection = database.getConnection(); var statement = connection.prepareStatement(
                "INSERT INTO egress_policies (conference_uuid, updated_at) VALUES (?, ?)")) {
            statement.setString(1, "legacy-conf");
            statement.setString(2, Instant.now().toString());
            statement.executeUpdate();
        }
        assertFalse(repository.findByConference("legacy-conf").orElseThrow().allowAll());
    }
}
