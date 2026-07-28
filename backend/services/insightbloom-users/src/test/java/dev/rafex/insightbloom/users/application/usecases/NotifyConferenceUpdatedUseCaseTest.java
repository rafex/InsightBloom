package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.Conference;
import dev.rafex.insightbloom.users.domain.model.EventCapability;
import dev.rafex.insightbloom.users.domain.model.EventType;
import dev.rafex.insightbloom.users.domain.model.Reservation;
import dev.rafex.insightbloom.users.domain.model.User;
import dev.rafex.insightbloom.users.domain.model.UserRole;
import dev.rafex.insightbloom.users.domain.ports.EmailPort;
import dev.rafex.insightbloom.users.domain.ports.EventTypeRepository;
import dev.rafex.insightbloom.users.domain.ports.ReservationRepository;
import dev.rafex.insightbloom.users.domain.ports.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NotifyConferenceUpdatedUseCaseTest {

    private ReservationRepository reservations;
    private UserRepository users;
    private EmailPort emailPort;
    private EventTypeRepository eventTypes;

    private NotifyConferenceUpdatedUseCase newUseCase(final boolean emailEnabled) {
        reservations = mock(ReservationRepository.class);
        users = mock(UserRepository.class);
        emailPort = mock(EmailPort.class);
        eventTypes = mock(EventTypeRepository.class);
        when(emailPort.isEnabled()).thenReturn(emailEnabled);
        return new NotifyConferenceUpdatedUseCase(reservations, users, emailPort, eventTypes);
    }

    private void withOneAttendee(final String conferenceUuid) {
        final var reservation = new Reservation(conferenceUuid, "user-a", null);
        when(reservations.findByConference(conferenceUuid)).thenReturn(List.of(reservation));
        when(users.findByUuid("user-a")).thenReturn(Optional.of(
                new User("user-a", "user-a", "user-a", "a@x.com", UserRole.ATTENDEE)));
    }

    @Test
    void sendsNotificationWhenNameChanges() {
        final var useCase = newUseCase(true);
        final var before = new Conference("event", "Evento viejo", "owner");
        final var after = new Conference("event", "Evento viejo", "owner");
        after.setName("Evento nuevo");
        withOneAttendee(after.getUuid());

        useCase.execute(before, after);

        verify(emailPort).sendHtml(eq("a@x.com"), contains("Evento nuevo"), anyString());
    }

    @Test
    void doesNotSendWhenNothingChanged() {
        final var useCase = newUseCase(true);
        final var conference = new Conference("event", "Evento", "owner");
        withOneAttendee(conference.getUuid());

        useCase.execute(conference, conference);

        verifyNoInteractions(emailPort);
    }

    @Test
    void doesNotSendWhenEmailProviderDisabled() {
        final var useCase = newUseCase(false);
        final var before = new Conference("event", "Evento viejo", "owner");
        final var after = new Conference("event", "Evento viejo", "owner");
        after.setName("Evento nuevo");

        useCase.execute(before, after);

        verify(emailPort, never()).sendHtml(anyString(), anyString(), anyString());
    }

    @Test
    void renderedEmailIncludesUnchangedFieldsToo() {
        final var useCase = newUseCase(true);
        final var before = new Conference("event", "Evento", "owner");
        before.setVenue("Auditorio Central");
        final var after = new Conference("event", "Evento", "owner");
        after.setVenue("Auditorio Central");
        after.setName("Evento actualizado");
        withOneAttendee(after.getUuid());

        final ArgumentCaptor<String> htmlCaptor = ArgumentCaptor.forClass(String.class);
        useCase.execute(before, after);

        verify(emailPort).sendHtml(eq("a@x.com"), anyString(), htmlCaptor.capture());
        assertTrue(htmlCaptor.getValue().contains("Auditorio Central"));
    }

    @Test
    void resolvesEventTypeDisplayNameOnChange() {
        final var useCase = newUseCase(true);
        final var before = new Conference("event", "Evento", "owner");
        final var after = new Conference("event", "Evento", "owner");
        after.setEventTypeKey("workshop");
        when(eventTypes.findByKey("conference")).thenReturn(Optional.of(
                new EventType("conference", "Conferencia", null, Set.of(EventCapability.TICKETING_GENERAL))));
        when(eventTypes.findByKey("workshop")).thenReturn(Optional.of(
                new EventType("workshop", "Taller", null, Set.of(EventCapability.TICKETING_GENERAL))));
        withOneAttendee(after.getUuid());

        final ArgumentCaptor<String> htmlCaptor = ArgumentCaptor.forClass(String.class);
        useCase.execute(before, after);

        verify(emailPort).sendHtml(eq("a@x.com"), anyString(), htmlCaptor.capture());
        assertTrue(htmlCaptor.getValue().contains("Taller"));
    }
}
