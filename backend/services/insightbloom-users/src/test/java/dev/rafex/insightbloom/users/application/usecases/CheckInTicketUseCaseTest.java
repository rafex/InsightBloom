package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.Reservation;
import dev.rafex.insightbloom.users.domain.model.ReservationStatus;
import dev.rafex.insightbloom.users.domain.ports.ReservationRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class CheckInTicketUseCaseTest {

    @Test
    void execute_firstScan_marksCheckedIn() {
        final ReservationRepository repo = Mockito.mock(ReservationRepository.class);
        final Reservation reservation = new Reservation("conf-1", "user-1", null);
        Mockito.when(repo.findByTicketCode("conf-1", reservation.getTicketCode())).thenReturn(Optional.of(reservation));

        final Reservation result = new CheckInTicketUseCase(repo).execute("conf-1", reservation.getTicketCode());

        assertEquals(ReservationStatus.CHECKED_IN, result.getStatus());
        assertNotNull(result.getCheckedInAt());
        Mockito.verify(repo).save(reservation);
    }

    @Test
    void execute_secondScan_throwsAlreadyCheckedIn() {
        final ReservationRepository repo = Mockito.mock(ReservationRepository.class);
        final Reservation reservation = new Reservation("conf-1", "user-1", null);
        reservation.markCheckedIn();
        Mockito.when(repo.findByTicketCode("conf-1", reservation.getTicketCode())).thenReturn(Optional.of(reservation));

        final var useCase = new CheckInTicketUseCase(repo);
        final var ex = assertThrows(IllegalStateException.class,
                () -> useCase.execute("conf-1", reservation.getTicketCode()));
        assertEquals("already_checked_in", ex.getMessage());
    }

    @Test
    void execute_ticketFromDifferentConference_throwsNotFound() {
        final ReservationRepository repo = Mockito.mock(ReservationRepository.class);
        Mockito.when(repo.findByTicketCode("conf-2", "some-code")).thenReturn(Optional.empty());

        final var useCase = new CheckInTicketUseCase(repo);
        final var ex = assertThrows(IllegalArgumentException.class, () -> useCase.execute("conf-2", "some-code"));
        assertEquals("ticket_not_found", ex.getMessage());
    }
}
