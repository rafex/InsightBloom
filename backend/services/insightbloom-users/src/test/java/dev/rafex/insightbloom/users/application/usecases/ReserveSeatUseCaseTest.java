package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.Conference;
import dev.rafex.insightbloom.users.domain.model.Reservation;
import dev.rafex.insightbloom.users.domain.model.VenueSeat;
import dev.rafex.insightbloom.users.domain.ports.ConferenceRepository;
import dev.rafex.insightbloom.users.domain.ports.EmailPort;
import dev.rafex.insightbloom.users.domain.ports.ReservationRepository;
import dev.rafex.insightbloom.users.domain.ports.UserRepository;
import dev.rafex.insightbloom.users.domain.ports.VenueSeatRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ReserveSeatUseCaseTest {

    private Conference seatedConference() {
        final Conference c = new Conference("charla-2026", "Charla 2026", "organizer-1");
        c.setSeatingMode("SEATED");
        return c;
    }

    private ReserveSeatUseCase newUseCase(final ConferenceRepository conferenceRepo,
                                           final ReservationRepository reservationRepo,
                                           final VenueSeatRepository venueSeatRepo) {
        final EmailPort emailPort = Mockito.mock(EmailPort.class);
        Mockito.when(emailPort.isEnabled()).thenReturn(false);
        final UserRepository userRepo = Mockito.mock(UserRepository.class);
        return new ReserveSeatUseCase(conferenceRepo, reservationRepo, venueSeatRepo, userRepo, emailPort, "https://app.test");
    }

    @Test
    void execute_seatAvailable_createsReservation() {
        final ConferenceRepository conferenceRepo = Mockito.mock(ConferenceRepository.class);
        final ReservationRepository reservationRepo = Mockito.mock(ReservationRepository.class);
        final VenueSeatRepository venueSeatRepo = Mockito.mock(VenueSeatRepository.class);
        final Conference conference = seatedConference();
        final VenueSeat seat = new VenueSeat(conference.getUuid(), "A1", 0.5, 0.5);
        Mockito.when(conferenceRepo.findByUuid(conference.getUuid())).thenReturn(Optional.of(conference));
        Mockito.when(venueSeatRepo.findByConference(conference.getUuid())).thenReturn(List.of(seat));
        Mockito.when(reservationRepo.findByConferenceAndUser(conference.getUuid(), "user-1")).thenReturn(Optional.empty());

        final Reservation reservation = newUseCase(conferenceRepo, reservationRepo, venueSeatRepo)
                .execute(conference.getUuid(), "user-1", seat.getUuid());

        assertEquals(seat.getUuid(), reservation.getSeatUuid());
        Mockito.verify(reservationRepo).insertNew(Mockito.any());
    }

    @Test
    void execute_seatAlreadyTaken_throwsSeatAlreadyTaken() {
        final ConferenceRepository conferenceRepo = Mockito.mock(ConferenceRepository.class);
        final ReservationRepository reservationRepo = Mockito.mock(ReservationRepository.class);
        final VenueSeatRepository venueSeatRepo = Mockito.mock(VenueSeatRepository.class);
        final Conference conference = seatedConference();
        final VenueSeat seat = new VenueSeat(conference.getUuid(), "A1", 0.5, 0.5);
        Mockito.when(conferenceRepo.findByUuid(conference.getUuid())).thenReturn(Optional.of(conference));
        Mockito.when(venueSeatRepo.findByConference(conference.getUuid())).thenReturn(List.of(seat));
        Mockito.when(reservationRepo.findByConferenceAndUser(conference.getUuid(), "user-2")).thenReturn(Optional.empty());
        Mockito.doThrow(new RuntimeException("[SQLITE_CONSTRAINT]  UNIQUE constraint failed: reservations.conference_uuid, reservations.seat_uuid"))
                .when(reservationRepo).insertNew(Mockito.any());

        final var useCase = newUseCase(conferenceRepo, reservationRepo, venueSeatRepo);
        final var ex = assertThrows(IllegalStateException.class,
                () -> useCase.execute(conference.getUuid(), "user-2", seat.getUuid()));
        assertEquals("seat_already_taken", ex.getMessage());
    }

    @Test
    void execute_seatNotFound_throws() {
        final ConferenceRepository conferenceRepo = Mockito.mock(ConferenceRepository.class);
        final ReservationRepository reservationRepo = Mockito.mock(ReservationRepository.class);
        final VenueSeatRepository venueSeatRepo = Mockito.mock(VenueSeatRepository.class);
        final Conference conference = seatedConference();
        Mockito.when(conferenceRepo.findByUuid(conference.getUuid())).thenReturn(Optional.of(conference));
        Mockito.when(venueSeatRepo.findByConference(conference.getUuid())).thenReturn(List.of());

        final var useCase = newUseCase(conferenceRepo, reservationRepo, venueSeatRepo);
        assertThrows(IllegalArgumentException.class, () -> useCase.execute(conference.getUuid(), "user-3", "missing-seat"));
    }

    @Test
    void execute_notSeatedMode_throws() {
        final ConferenceRepository conferenceRepo = Mockito.mock(ConferenceRepository.class);
        final ReservationRepository reservationRepo = Mockito.mock(ReservationRepository.class);
        final VenueSeatRepository venueSeatRepo = Mockito.mock(VenueSeatRepository.class);
        final Conference conference = new Conference("charla-2026", "Charla 2026", "organizer-1");
        conference.setSeatingMode("GENERAL");
        Mockito.when(conferenceRepo.findByUuid(conference.getUuid())).thenReturn(Optional.of(conference));

        final var useCase = newUseCase(conferenceRepo, reservationRepo, venueSeatRepo);
        assertThrows(IllegalStateException.class, () -> useCase.execute(conference.getUuid(), "user-4", "seat-x"));
    }
}
