package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.Conference;
import dev.rafex.insightbloom.users.domain.model.Reservation;
import dev.rafex.insightbloom.users.domain.model.User;
import dev.rafex.insightbloom.users.domain.model.UserRole;
import dev.rafex.insightbloom.users.domain.ports.ConferenceRepository;
import dev.rafex.insightbloom.users.domain.ports.ReservationRepository;
import dev.rafex.insightbloom.users.domain.ports.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ReserveGeneralUseCaseTest {

    private Conference generalConference() {
        final Conference c = new Conference("charla-2026", "Charla 2026", "organizer-1");
        c.setSeatingMode("GENERAL");
        c.setCapacity(1);
        return c;
    }

    /** Attendee comun (no exento de boleto) por defecto, salvo que el test stubee otra cosa. */
    private UserRepository mockUserRepository(final String userUuid) {
        final UserRepository userRepo = Mockito.mock(UserRepository.class);
        final User user = new User(userUuid, userUuid, userUuid, null, null, java.util.List.of(),
                false, false, Set.of(UserRole.ATTENDEE));
        Mockito.when(userRepo.findByUuid(userUuid)).thenReturn(Optional.of(user));
        return userRepo;
    }

    @Test
    void execute_capacityAvailable_createsReservation() {
        final ConferenceRepository conferenceRepo = Mockito.mock(ConferenceRepository.class);
        final ReservationRepository reservationRepo = Mockito.mock(ReservationRepository.class);
        final UserRepository userRepo = mockUserRepository("user-1");
        final Conference conference = generalConference();
        Mockito.when(conferenceRepo.findByUuid(conference.getUuid())).thenReturn(Optional.of(conference));
        Mockito.when(reservationRepo.findByConferenceAndUser(conference.getUuid(), "user-1")).thenReturn(Optional.empty());
        Mockito.when(conferenceRepo.tryIncrementReservedCount(conference.getUuid())).thenReturn(true);

        final Reservation reservation = new ReserveGeneralUseCase(conferenceRepo, reservationRepo, userRepo)
                .execute(conference.getUuid(), "user-1");

        assertNotNull(reservation.getTicketCode());
        assertEquals("user-1", reservation.getUserUuid());
        assertNull(reservation.getSeatUuid());
        Mockito.verify(reservationRepo).insertNew(Mockito.any());
    }

    @Test
    void execute_capacityFull_throwsCapacityExceeded() {
        final ConferenceRepository conferenceRepo = Mockito.mock(ConferenceRepository.class);
        final ReservationRepository reservationRepo = Mockito.mock(ReservationRepository.class);
        final UserRepository userRepo = mockUserRepository("user-2");
        final Conference conference = generalConference();
        Mockito.when(conferenceRepo.findByUuid(conference.getUuid())).thenReturn(Optional.of(conference));
        Mockito.when(reservationRepo.findByConferenceAndUser(conference.getUuid(), "user-2")).thenReturn(Optional.empty());
        Mockito.when(conferenceRepo.tryIncrementReservedCount(conference.getUuid())).thenReturn(false);

        final var useCase = new ReserveGeneralUseCase(conferenceRepo, reservationRepo, userRepo);
        final var ex = assertThrows(IllegalStateException.class, () -> useCase.execute(conference.getUuid(), "user-2"));
        assertEquals("capacity_exceeded", ex.getMessage());
        Mockito.verify(reservationRepo, Mockito.never()).insertNew(Mockito.any());
    }

    @Test
    void execute_alreadyReserved_returnsExistingReservation() {
        final ConferenceRepository conferenceRepo = Mockito.mock(ConferenceRepository.class);
        final ReservationRepository reservationRepo = Mockito.mock(ReservationRepository.class);
        final UserRepository userRepo = mockUserRepository("user-3");
        final Conference conference = generalConference();
        final Reservation existing = new Reservation(conference.getUuid(), "user-3", null);
        Mockito.when(conferenceRepo.findByUuid(conference.getUuid())).thenReturn(Optional.of(conference));
        Mockito.when(reservationRepo.findByConferenceAndUser(conference.getUuid(), "user-3")).thenReturn(Optional.of(existing));

        final Reservation result = new ReserveGeneralUseCase(conferenceRepo, reservationRepo, userRepo)
                .execute(conference.getUuid(), "user-3");

        assertEquals(existing.getUuid(), result.getUuid());
        Mockito.verify(conferenceRepo, Mockito.never()).tryIncrementReservedCount(Mockito.any());
    }

    @Test
    void execute_notGeneralMode_throws() {
        final ConferenceRepository conferenceRepo = Mockito.mock(ConferenceRepository.class);
        final ReservationRepository reservationRepo = Mockito.mock(ReservationRepository.class);
        final UserRepository userRepo = mockUserRepository("user-4");
        final Conference conference = new Conference("charla-2026", "Charla 2026", "organizer-1");
        conference.setSeatingMode("SEATED");
        Mockito.when(conferenceRepo.findByUuid(conference.getUuid())).thenReturn(Optional.of(conference));

        final var useCase = new ReserveGeneralUseCase(conferenceRepo, reservationRepo, userRepo);
        assertThrows(IllegalStateException.class, () -> useCase.execute(conference.getUuid(), "user-4"));
    }

    @Test
    void execute_staffRole_throwsExemptAndNeverConsumesCapacity() {
        final ConferenceRepository conferenceRepo = Mockito.mock(ConferenceRepository.class);
        final ReservationRepository reservationRepo = Mockito.mock(ReservationRepository.class);
        final UserRepository userRepo = Mockito.mock(UserRepository.class);
        final Conference conference = generalConference();
        final User moderator = new User("mod-1", "mod-1", "mod-1", null, null, java.util.List.of(),
                false, false, Set.of(UserRole.MODERATOR));
        Mockito.when(conferenceRepo.findByUuid(conference.getUuid())).thenReturn(Optional.of(conference));
        Mockito.when(userRepo.findByUuid("mod-1")).thenReturn(Optional.of(moderator));

        final var useCase = new ReserveGeneralUseCase(conferenceRepo, reservationRepo, userRepo);
        final var ex = assertThrows(IllegalStateException.class, () -> useCase.execute(conference.getUuid(), "mod-1"));
        assertEquals("staff_exempt_no_ticket_needed", ex.getMessage());
        Mockito.verify(conferenceRepo, Mockito.never()).tryIncrementReservedCount(Mockito.any());
        Mockito.verify(reservationRepo, Mockito.never()).insertNew(Mockito.any());
    }
}
