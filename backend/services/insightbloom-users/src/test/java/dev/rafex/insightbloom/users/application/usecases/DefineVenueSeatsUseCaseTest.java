package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.Conference;
import dev.rafex.insightbloom.users.domain.model.Reservation;
import dev.rafex.insightbloom.users.domain.model.VenueSeat;
import dev.rafex.insightbloom.users.domain.ports.ConferenceRepository;
import dev.rafex.insightbloom.users.domain.ports.ReservationRepository;
import dev.rafex.insightbloom.users.domain.ports.VenueSeatRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class DefineVenueSeatsUseCaseTest {

    private Conference conference() {
        return new Conference("charla-2026", "Charla 2026", "organizer-1");
    }

    @Test
    void execute_noConflicts_replacesAllSeats() {
        final ConferenceRepository conferenceRepo = Mockito.mock(ConferenceRepository.class);
        final VenueSeatRepository venueSeatRepo = Mockito.mock(VenueSeatRepository.class);
        final ReservationRepository reservationRepo = Mockito.mock(ReservationRepository.class);
        final Conference conf = conference();
        Mockito.when(conferenceRepo.findByUuid(conf.getUuid())).thenReturn(Optional.of(conf));
        Mockito.when(venueSeatRepo.findByConference(conf.getUuid())).thenReturn(List.of());
        Mockito.when(reservationRepo.findByConference(conf.getUuid())).thenReturn(List.of());

        final var useCase = new DefineVenueSeatsUseCase(conferenceRepo, venueSeatRepo, reservationRepo);
        final var result = useCase.execute(conf.getUuid(), "organizer-1",
                List.of(new DefineVenueSeatsUseCase.SeatInput(null, "A1", 0.1, 0.2)));

        assertTrue(result.isPresent());
        assertEquals(1, result.get().size());
        Mockito.verify(venueSeatRepo).deleteByConference(conf.getUuid());
        Mockito.verify(venueSeatRepo).save(Mockito.any());
    }

    @Test
    void execute_removingReservedSeat_throwsSeatHasActiveReservation() {
        final ConferenceRepository conferenceRepo = Mockito.mock(ConferenceRepository.class);
        final VenueSeatRepository venueSeatRepo = Mockito.mock(VenueSeatRepository.class);
        final ReservationRepository reservationRepo = Mockito.mock(ReservationRepository.class);
        final Conference conf = conference();
        final VenueSeat existingSeat = new VenueSeat(conf.getUuid(), "A1", 0.1, 0.2);
        final Reservation reservation = new Reservation(conf.getUuid(), "user-1", existingSeat.getUuid());
        Mockito.when(conferenceRepo.findByUuid(conf.getUuid())).thenReturn(Optional.of(conf));
        Mockito.when(venueSeatRepo.findByConference(conf.getUuid())).thenReturn(List.of(existingSeat));
        Mockito.when(reservationRepo.findByConference(conf.getUuid())).thenReturn(List.of(reservation));

        final var useCase = new DefineVenueSeatsUseCase(conferenceRepo, venueSeatRepo, reservationRepo);
        final var ex = assertThrows(IllegalStateException.class, () -> useCase.execute(conf.getUuid(), "organizer-1",
                List.of(new DefineVenueSeatsUseCase.SeatInput(null, "B1", 0.3, 0.4))));
        assertEquals("seat_has_active_reservation", ex.getMessage());
        Mockito.verify(venueSeatRepo, Mockito.never()).deleteByConference(Mockito.any());
    }

    @Test
    void execute_keepingReservedSeatByUuid_succeeds() {
        final ConferenceRepository conferenceRepo = Mockito.mock(ConferenceRepository.class);
        final VenueSeatRepository venueSeatRepo = Mockito.mock(VenueSeatRepository.class);
        final ReservationRepository reservationRepo = Mockito.mock(ReservationRepository.class);
        final Conference conf = conference();
        final VenueSeat existingSeat = new VenueSeat(conf.getUuid(), "A1", 0.1, 0.2);
        final Reservation reservation = new Reservation(conf.getUuid(), "user-1", existingSeat.getUuid());
        Mockito.when(conferenceRepo.findByUuid(conf.getUuid())).thenReturn(Optional.of(conf));
        Mockito.when(venueSeatRepo.findByConference(conf.getUuid())).thenReturn(List.of(existingSeat));
        Mockito.when(reservationRepo.findByConference(conf.getUuid())).thenReturn(List.of(reservation));

        final var useCase = new DefineVenueSeatsUseCase(conferenceRepo, venueSeatRepo, reservationRepo);
        final var result = useCase.execute(conf.getUuid(), "organizer-1",
                List.of(new DefineVenueSeatsUseCase.SeatInput(existingSeat.getUuid(), "A1", 0.15, 0.25)));

        assertTrue(result.isPresent());
        Mockito.verify(venueSeatRepo).deleteByConference(conf.getUuid());
    }
}
