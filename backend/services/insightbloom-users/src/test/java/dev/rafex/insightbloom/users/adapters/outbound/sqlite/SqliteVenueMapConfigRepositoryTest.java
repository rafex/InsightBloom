package dev.rafex.insightbloom.users.adapters.outbound.sqlite;

import dev.rafex.insightbloom.users.application.usecases.CloneVenueMapUseCase;
import dev.rafex.insightbloom.users.application.usecases.DefineVenueSeatsUseCase;
import dev.rafex.insightbloom.users.application.usecases.VenueMapConfigUseCase;
import dev.rafex.insightbloom.users.domain.model.Conference;
import dev.rafex.insightbloom.users.domain.model.VenueMapConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class SqliteVenueMapConfigRepositoryTest {
    @Test
    void configSurvivesDatabaseReinitialization(@TempDir final Path tempDir) {
        final DatabaseManager db = new DatabaseManager(tempDir.resolve("users.db").toString()); db.initialize();
        final var repo = new SqliteVenueMapConfigRepository(db);
        repo.save(new VenueMapConfig("event-1", "SEATMAP_CANVAS", "{\"version\":1}"));
        db.initialize();
        assertEquals(new VenueMapConfig("event-1", "SEATMAP_CANVAS", "{\"version\":1}"), repo.findByConference("event-1").orElseThrow());
    }

    @Test
    void onlyOwnerCanSaveValidLayoutJson(@TempDir final Path tempDir) {
        final DatabaseManager db = new DatabaseManager(tempDir.resolve("users.db").toString()); db.initialize();
        final var conferences = new SqliteConferenceRepository(db);
        final var configs = new SqliteVenueMapConfigRepository(db);
        final Conference event = new Conference("owner-check", "Owner check", "owner"); conferences.save(event);
        final var useCase = new VenueMapConfigUseCase(conferences, configs);
        assertTrue(useCase.set(event.getUuid(), "another-user", "SEATMAP_CANVAS", "{}").isEmpty());
        assertThrows(IllegalArgumentException.class, () -> useCase.set(event.getUuid(), "owner", "SEATMAP_CANVAS", "not-json"));
        assertEquals("SEATMAP_CANVAS", useCase.set(event.getUuid(), "owner", "SEATMAP_CANVAS", "{\"version\":1}").orElseThrow().engine());
    }

    @Test
    void cloneCopiesLayoutAndCreatesNewSeatIdsWithoutReservations(@TempDir final Path tempDir) {
        final DatabaseManager db = new DatabaseManager(tempDir.resolve("users.db").toString()); db.initialize();
        final var conferences = new SqliteConferenceRepository(db);
        final var configs = new SqliteVenueMapConfigRepository(db);
        final var seats = new SqliteVenueSeatRepository(db);
        final var reservations = new SqliteReservationRepository(db);
        final Conference source = new Conference("source", "Source", "owner");
        final Conference target = new Conference("target", "Target", "owner");
        conferences.save(source); conferences.save(target);
        seats.save(new dev.rafex.insightbloom.users.domain.model.VenueSeat(source.getUuid(), "A1", .2, .3));
        configs.save(new VenueMapConfig(source.getUuid(), "SEATMAP_CANVAS", "{\"version\":1,\"sections\":[]}"));
        final var defineSeats = new DefineVenueSeatsUseCase(conferences, seats, reservations);
        final var useCase = new CloneVenueMapUseCase(conferences, configs, seats, defineSeats);

        final VenueMapConfig result = useCase.execute(target.getUuid(), source.getUuid(), "owner").orElseThrow();

        assertEquals("SEATMAP_CANVAS", result.engine());
        assertEquals("{\"version\":1,\"sections\":[]}", result.layoutJson());
        final var copiedSeat = seats.findByConference(target.getUuid()).getFirst();
        assertEquals("A1", copiedSeat.getLabel());
        assertNotEquals(seats.findByConference(source.getUuid()).getFirst().getUuid(), copiedSeat.getUuid());
        assertTrue(reservations.findByConference(target.getUuid()).isEmpty());
    }
}
