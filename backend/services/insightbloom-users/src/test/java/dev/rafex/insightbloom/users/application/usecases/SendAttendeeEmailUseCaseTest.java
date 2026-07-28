package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.Conference;
import dev.rafex.insightbloom.users.domain.model.Reservation;
import dev.rafex.insightbloom.users.domain.model.User;
import dev.rafex.insightbloom.users.domain.model.UserRole;
import dev.rafex.insightbloom.users.domain.ports.ConferenceRepository;
import dev.rafex.insightbloom.users.domain.ports.EmailPort;
import dev.rafex.insightbloom.users.domain.ports.ReservationRepository;
import dev.rafex.insightbloom.users.domain.ports.UserRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SendAttendeeEmailUseCaseTest {

    private static Reservation reservationOf(final String conferenceUuid, final String userUuid) {
        return new Reservation(conferenceUuid, userUuid, null);
    }

    @Test
    void sendsToAllAttendeesWhenNoRecipientUuidsGiven() {
        final ConferenceRepository conferences = mock(ConferenceRepository.class);
        final ReservationRepository reservations = mock(ReservationRepository.class);
        final UserRepository users = mock(UserRepository.class);
        final EmailPort emailPort = mock(EmailPort.class);

        final var conference = new Conference("event", "Evento", "owner");
        when(conferences.findByUuid(conference.getUuid())).thenReturn(Optional.of(conference));
        when(emailPort.isEnabled()).thenReturn(true);

        final var r1 = reservationOf(conference.getUuid(), "user-a");
        final var r2 = reservationOf(conference.getUuid(), "user-b");
        when(reservations.findByConference(conference.getUuid())).thenReturn(List.of(r1, r2));
        when(users.findByUuid("user-a")).thenReturn(Optional.of(userWithEmail("user-a", "a@x.com")));
        when(users.findByUuid("user-b")).thenReturn(Optional.of(userWithEmail("user-b", "b@x.com")));

        final var useCase = new SendAttendeeEmailUseCase(conferences, reservations, users, emailPort);
        final var summary = useCase.execute(conference.getUuid(),
                new SendAttendeeEmailUseCase.SendRequest("Aviso", "Hola a todos", null));

        assertEquals(2, summary.sent());
        assertEquals(0, summary.skipped());
        verify(emailPort).sendHtml(eq("a@x.com"), eq("Aviso"), anyString());
        verify(emailPort).sendHtml(eq("b@x.com"), eq("Aviso"), anyString());
    }

    @Test
    void sendsOnlyToRequestedRecipientWhenRecipientUuidsGiven() {
        final ConferenceRepository conferences = mock(ConferenceRepository.class);
        final ReservationRepository reservations = mock(ReservationRepository.class);
        final UserRepository users = mock(UserRepository.class);
        final EmailPort emailPort = mock(EmailPort.class);

        final var conference = new Conference("event", "Evento", "owner");
        when(conferences.findByUuid(conference.getUuid())).thenReturn(Optional.of(conference));
        when(emailPort.isEnabled()).thenReturn(true);

        final var r1 = reservationOf(conference.getUuid(), "user-a");
        final var r2 = reservationOf(conference.getUuid(), "user-b");
        when(reservations.findByConference(conference.getUuid())).thenReturn(List.of(r1, r2));
        when(users.findByUuid("user-a")).thenReturn(Optional.of(userWithEmail("user-a", "a@x.com")));
        when(users.findByUuid("user-b")).thenReturn(Optional.of(userWithEmail("user-b", "b@x.com")));

        final var useCase = new SendAttendeeEmailUseCase(conferences, reservations, users, emailPort);
        final var summary = useCase.execute(conference.getUuid(),
                new SendAttendeeEmailUseCase.SendRequest("Aviso", "Solo para vos", List.of("user-b")));

        assertEquals(1, summary.sent());
        verify(emailPort, never()).sendHtml(eq("a@x.com"), anyString(), anyString());
        verify(emailPort).sendHtml(eq("b@x.com"), eq("Aviso"), anyString());
    }

    @Test
    void rejectsWhenEmailProviderNotConfigured() {
        final ConferenceRepository conferences = mock(ConferenceRepository.class);
        final ReservationRepository reservations = mock(ReservationRepository.class);
        final UserRepository users = mock(UserRepository.class);
        final EmailPort emailPort = mock(EmailPort.class);

        final var conference = new Conference("event", "Evento", "owner");
        when(conferences.findByUuid(conference.getUuid())).thenReturn(Optional.of(conference));
        when(emailPort.isEnabled()).thenReturn(false);

        final var useCase = new SendAttendeeEmailUseCase(conferences, reservations, users, emailPort);
        assertThrows(IllegalStateException.class, () -> useCase.execute(conference.getUuid(),
                new SendAttendeeEmailUseCase.SendRequest("Aviso", "Hola", null)));
    }

    @Test
    void rejectsWhenNoRecipientsResolve() {
        final ConferenceRepository conferences = mock(ConferenceRepository.class);
        final ReservationRepository reservations = mock(ReservationRepository.class);
        final UserRepository users = mock(UserRepository.class);
        final EmailPort emailPort = mock(EmailPort.class);

        final var conference = new Conference("event", "Evento", "owner");
        when(conferences.findByUuid(conference.getUuid())).thenReturn(Optional.of(conference));
        when(emailPort.isEnabled()).thenReturn(true);
        when(reservations.findByConference(conference.getUuid())).thenReturn(List.of());

        final var useCase = new SendAttendeeEmailUseCase(conferences, reservations, users, emailPort);
        assertThrows(IllegalArgumentException.class, () -> useCase.execute(conference.getUuid(),
                new SendAttendeeEmailUseCase.SendRequest("Aviso", "Hola", null)));
    }

    @Test
    void rejectsBlankSubjectOrMessage() {
        final ConferenceRepository conferences = mock(ConferenceRepository.class);
        final ReservationRepository reservations = mock(ReservationRepository.class);
        final UserRepository users = mock(UserRepository.class);
        final EmailPort emailPort = mock(EmailPort.class);
        final var useCase = new SendAttendeeEmailUseCase(conferences, reservations, users, emailPort);

        assertThrows(IllegalArgumentException.class, () -> useCase.execute("conf",
                new SendAttendeeEmailUseCase.SendRequest("", "Hola", null)));
        assertThrows(IllegalArgumentException.class, () -> useCase.execute("conf",
                new SendAttendeeEmailUseCase.SendRequest("Aviso", "  ", null)));
    }

    private static User userWithEmail(final String uuid, final String email) {
        return new User(uuid, uuid, uuid, email, UserRole.ATTENDEE);
    }
}
