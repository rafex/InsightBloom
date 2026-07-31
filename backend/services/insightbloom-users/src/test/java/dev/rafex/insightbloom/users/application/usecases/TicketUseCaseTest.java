package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.EventCapability;
import dev.rafex.insightbloom.users.domain.model.EventType;
import dev.rafex.insightbloom.users.domain.model.Ticket;
import dev.rafex.insightbloom.users.domain.model.UserRole;
import dev.rafex.insightbloom.users.domain.ports.*;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TicketUseCaseTest {
    @Test
    void acceptsUuidV4AndQrPayloadContainingIt() {
        final String uuid = "123e4567-e89b-42d3-a456-426614174000";
        assertEquals(uuid, TicketUseCase.normalizeCode(uuid));
        assertEquals(uuid, TicketUseCase.normalizeCode("https://example.test/ticket?ticket=" + uuid));
    }

    @Test
    void claimsTicketGloballyFromQrPayloadWithoutConferenceId() {
        final ConferenceRepository conferences = mock(ConferenceRepository.class);
        final EventTypeRepository eventTypes = mock(EventTypeRepository.class);
        final TicketRepository tickets = mock(TicketRepository.class);
        final ConferenceMembershipRepository memberships = mock(ConferenceMembershipRepository.class);
        final ReservationRepository reservations = mock(ReservationRepository.class);
        final var conference = new dev.rafex.insightbloom.users.domain.model.Conference(
                "event", "Evento", "owner");
        final var ticket = new Ticket(conference.getUuid(), "owner", null, null);
        when(conferences.findByUuid(conference.getUuid())).thenReturn(Optional.of(conference));
        when(eventTypes.findByKey("conference")).thenReturn(Optional.of(
                new EventType("conference", "Conferencia", null, Set.of(EventCapability.TICKETING_GENERAL))));
        when(tickets.findByTicketCode(ticket.getTicketCode())).thenReturn(Optional.of(ticket));
        when(tickets.findByCode(conference.getUuid(), ticket.getTicketCode())).thenReturn(Optional.of(ticket));
        when(tickets.claim(eq(ticket.getUuid()), eq("user"), anyString())).thenReturn(true);
        when(tickets.findByUuid(ticket.getUuid())).thenReturn(Optional.of(ticket));
        when(memberships.exists("user", conference.getUuid())).thenReturn(false);

        final var useCase = new TicketUseCase(conferences, eventTypes, tickets, memberships,
                mock(EmailPort.class), "", reservations);

        assertSame(ticket, useCase.claimByCode(
                "https://example.test/ticket?ticket=" + ticket.getTicketCode(), "user"));
        verify(tickets).findByTicketCode(ticket.getTicketCode());
        verify(tickets).claim(eq(ticket.getUuid()), eq("user"), anyString());
    }

    @Test
    void rejectsNonV4ManualCode() {
        assertThrows(IllegalArgumentException.class,
                () -> TicketUseCase.normalizeCode("123e4567-e89b-12d3-a456-426614174000"));
    }

    @Test
    void claimsOnceAndRegistersMembership() {
        final ConferenceRepository conferences = mock(ConferenceRepository.class);
        final EventTypeRepository eventTypes = mock(EventTypeRepository.class);
        final TicketRepository tickets = mock(TicketRepository.class);
        final ConferenceMembershipRepository memberships = mock(ConferenceMembershipRepository.class);
        final EmailPort email = mock(EmailPort.class);
        final ReservationRepository reservations = mock(ReservationRepository.class);
        final var conference = new dev.rafex.insightbloom.users.domain.model.Conference("event", "Evento", "owner");
        final var ticket = new Ticket(conference.getUuid(), "owner", null, null);
        when(conferences.findByUuid(conference.getUuid())).thenReturn(Optional.of(conference));
        when(eventTypes.findByKey("conference")).thenReturn(Optional.of(
                new EventType("conference", "Conferencia", null, Set.of(EventCapability.TICKETING_GENERAL))));
        when(tickets.findByCode(conference.getUuid(), ticket.getTicketCode())).thenReturn(Optional.of(ticket));
        when(tickets.claim(eq(ticket.getUuid()), eq("user"), anyString())).thenReturn(true);
        when(tickets.findByUuid(ticket.getUuid())).thenReturn(Optional.of(ticket));
        when(memberships.exists("user", conference.getUuid())).thenReturn(false);
        final var useCase = new TicketUseCase(conferences, eventTypes, tickets, memberships, email,
                "https://frontend.test", reservations);

        final Ticket claimed = useCase.claim(conference.getUuid(), ticket.getTicketCode(), "user");

        assertSame(ticket, claimed);
        verify(tickets).claim(eq(ticket.getUuid()), eq("user"), anyString());
        verify(memberships).recordJoin(any());
    }

    @Test
    void rejectsClaimingASecondTicketByTheSameUser() {
        final ConferenceRepository conferences = mock(ConferenceRepository.class);
        final EventTypeRepository eventTypes = mock(EventTypeRepository.class);
        final TicketRepository tickets = mock(TicketRepository.class);
        final ConferenceMembershipRepository memberships = mock(ConferenceMembershipRepository.class);
        final EmailPort email = mock(EmailPort.class);
        final ReservationRepository reservations = mock(ReservationRepository.class);
        final var conference = new dev.rafex.insightbloom.users.domain.model.Conference("event", "Evento", "owner");
        final var alreadyClaimed = new Ticket(conference.getUuid(), "owner", null, null);
        final var secondTicket = new Ticket(conference.getUuid(), "owner", null, null);
        when(conferences.findByUuid(conference.getUuid())).thenReturn(Optional.of(conference));
        when(eventTypes.findByKey("conference")).thenReturn(Optional.of(
                new EventType("conference", "Conferencia", null, Set.of(EventCapability.TICKETING_GENERAL))));
        when(tickets.findByCode(conference.getUuid(), secondTicket.getTicketCode())).thenReturn(Optional.of(secondTicket));
        when(tickets.findByConferenceAndUser(conference.getUuid(), "user")).thenReturn(Optional.of(alreadyClaimed));
        final var useCase = new TicketUseCase(conferences, eventTypes, tickets, memberships, email,
                "https://frontend.test", reservations);

        final var thrown = assertThrows(IllegalStateException.class,
                () -> useCase.claim(conference.getUuid(), secondTicket.getTicketCode(), "user"));
        assertEquals("user_already_has_ticket", thrown.getMessage());
        verify(tickets, never()).claim(eq(secondTicket.getUuid()), anyString(), anyString());
    }

    @Test
    void expiredConferenceDoesNotGrantAccess() {
        final ConferenceRepository conferences = mock(ConferenceRepository.class);
        final EventTypeRepository eventTypes = mock(EventTypeRepository.class);
        final TicketRepository tickets = mock(TicketRepository.class);
        final ReservationRepository reservations = mock(ReservationRepository.class);
        final var expired = new dev.rafex.insightbloom.users.domain.model.Conference(
                "event", "Evento", "owner", Instant.now().minusSeconds(60));
        when(eventTypes.findByKey("conference")).thenReturn(Optional.of(
                new EventType("conference", "Conferencia", null, Set.of(EventCapability.TICKETING_GENERAL))));
        final var useCase = new TicketUseCase(conferences, eventTypes, tickets,
                mock(ConferenceMembershipRepository.class), mock(EmailPort.class), "", reservations);

        assertFalse(useCase.hasAccess(expired, "user"));
        verify(tickets).expireByConference(eq(expired.getUuid()), anyString());
    }

    @Test
    void revokedTicketDoesNotRetainAccessThroughReservation() {
        final ConferenceRepository conferences = mock(ConferenceRepository.class);
        final EventTypeRepository eventTypes = mock(EventTypeRepository.class);
        final TicketRepository tickets = mock(TicketRepository.class);
        final ReservationRepository reservations = mock(ReservationRepository.class);
        final var conference = new dev.rafex.insightbloom.users.domain.model.Conference(
                "event", "Evento", "owner");
        when(eventTypes.findByKey("conference")).thenReturn(Optional.of(
                new EventType("conference", "Conferencia", null, Set.of(EventCapability.TICKETING_GENERAL))));
        when(tickets.findByConferenceAndUser(conference.getUuid(), "user")).thenReturn(Optional.empty());
        when(reservations.findByConferenceAndUser(conference.getUuid(), "user"))
                .thenReturn(Optional.of(new dev.rafex.insightbloom.users.domain.model.Reservation(
                        conference.getUuid(), "user", null)));

        final var useCase = new TicketUseCase(conferences, eventTypes, tickets,
                mock(ConferenceMembershipRepository.class), mock(EmailPort.class), "", reservations);

        assertFalse(useCase.hasAccess(conference, "user"));
        verify(tickets).findByConferenceAndUser(conference.getUuid(), "user");
    }

    @Test
    void revokingTicketReleasesReservedCapacity() {
        final ConferenceRepository conferences = mock(ConferenceRepository.class);
        final EventTypeRepository eventTypes = mock(EventTypeRepository.class);
        final TicketRepository tickets = mock(TicketRepository.class);
        final ConferenceMembershipRepository memberships = mock(ConferenceMembershipRepository.class);
        final ReservationRepository reservations = mock(ReservationRepository.class);
        final var conference = new dev.rafex.insightbloom.users.domain.model.Conference(
                "event", "Evento", "owner");
        conference.setCapacity(10);
        final var ticket = new Ticket(conference.getUuid(), "owner", null, null);
        when(conferences.findByUuid(conference.getUuid())).thenReturn(Optional.of(conference));
        when(eventTypes.findByKey("conference")).thenReturn(Optional.of(
                new EventType("conference", "Conferencia", null, Set.of(EventCapability.TICKETING_GENERAL))));
        when(tickets.findByUuid(ticket.getUuid())).thenReturn(Optional.of(ticket));
        when(tickets.revoke(eq(ticket.getUuid()), eq("moderator"), anyString())).thenReturn(true);

        final var useCase = new TicketUseCase(conferences, eventTypes, tickets, memberships,
                mock(EmailPort.class), "", reservations);

        useCase.revoke(conference.getUuid(), ticket.getUuid(), "moderator");

        verify(conferences).decrementReservedCount(conference.getUuid());
    }

    @Test
    void operationalTicketIsCountedClaimedAndIdempotent() {
        final ConferenceRepository conferences = mock(ConferenceRepository.class);
        final EventTypeRepository eventTypes = mock(EventTypeRepository.class);
        final TicketRepository tickets = mock(TicketRepository.class);
        final var conference = new dev.rafex.insightbloom.users.domain.model.Conference("event", "Evento", "owner");
        conference.setCapacity(2);
        when(conferences.findByUuid(conference.getUuid())).thenReturn(Optional.of(conference));
        when(eventTypes.findByKey("conference")).thenReturn(Optional.of(
                new EventType("conference", "Conferencia", null, Set.of(EventCapability.TICKETING_GENERAL))));
        when(tickets.findOperationalByConferenceAndUser(conference.getUuid(), "owner"))
                .thenReturn(Optional.empty());
        when(conferences.tryIncrementReservedCount(conference.getUuid())).thenReturn(true);

        final var useCase = new TicketUseCase(conferences, eventTypes, tickets,
                mock(ConferenceMembershipRepository.class), mock(EmailPort.class), "", mock(ReservationRepository.class));

        final Ticket issued = useCase.issueOperational(conference.getUuid(), "owner");

        assertTrue(issued.isOperational());
        assertEquals("CLAIMED", issued.getStatus().name());
        assertEquals("owner", issued.getClaimedByUserUuid());
        verify(conferences).tryIncrementReservedCount(conference.getUuid());
        verify(tickets).insert(issued);

        when(tickets.findOperationalByConferenceAndUser(conference.getUuid(), "owner"))
                .thenReturn(Optional.of(issued));
        assertSame(issued, useCase.issueOperational(conference.getUuid(), "owner"));
        verify(conferences, times(1)).tryIncrementReservedCount(conference.getUuid());
    }

    @Test
    void managementSummaryIncludesCapacityAndClaimedUser() {
        final ConferenceRepository conferences = mock(ConferenceRepository.class);
        final EventTypeRepository eventTypes = mock(EventTypeRepository.class);
        final TicketRepository tickets = mock(TicketRepository.class);
        final UserRepository users = mock(UserRepository.class);
        final var conference = new dev.rafex.insightbloom.users.domain.model.Conference("event", "Evento", "owner");
        conference.setCapacity(5);
        conference.setReservedCount(3);
        final Ticket operational = Ticket.operational(conference.getUuid(), "owner", "moderator");
        when(conferences.findByUuid(conference.getUuid())).thenReturn(Optional.of(conference));
        when(eventTypes.findByKey("conference")).thenReturn(Optional.of(
                new EventType("conference", "Conferencia", null, Set.of(EventCapability.TICKETING_GENERAL))));
        when(tickets.findByConference(conference.getUuid())).thenReturn(java.util.List.of(operational));
        when(users.findByUuid("moderator")).thenReturn(Optional.of(
                new dev.rafex.insightbloom.users.domain.model.User("moderator", "mod", "Moderador", "mod@example.test", UserRole.MODERATOR)));

        final var useCase = new TicketUseCase(conferences, eventTypes, tickets,
                mock(ConferenceMembershipRepository.class), mock(EmailPort.class), "",
                mock(ReservationRepository.class), null, users);

        final var summary = useCase.listManagement(conference.getUuid());

        assertEquals(5, summary.capacity());
        assertEquals(3, summary.reservedCount());
        assertEquals(2, summary.remainingToIssue());
        assertEquals(1, summary.tickets().size());
        assertEquals("Moderador", summary.claimedUsers().get("moderator").displayName());
        assertEquals("Moderador", summary.ticketActors().get("moderator").displayName());
    }

    @Test
    void managementSummaryIncludesRevokingUserInTicketActors() {
        final ConferenceRepository conferences = mock(ConferenceRepository.class);
        final EventTypeRepository eventTypes = mock(EventTypeRepository.class);
        final TicketRepository tickets = mock(TicketRepository.class);
        final UserRepository users = mock(UserRepository.class);
        final var conference = new dev.rafex.insightbloom.users.domain.model.Conference("event", "Evento", "owner");
        final var revoked = new Ticket("ticket", conference.getUuid(), "code", "owner", null, null,
                dev.rafex.insightbloom.users.domain.model.TicketStatus.REVOKED, null,
                Instant.now(), null, null, "moderator", Instant.now());
        when(conferences.findByUuid(conference.getUuid())).thenReturn(Optional.of(conference));
        when(eventTypes.findByKey("conference")).thenReturn(Optional.of(
                new EventType("conference", "Conferencia", null, Set.of(EventCapability.TICKETING_GENERAL))));
        when(tickets.findByConference(conference.getUuid())).thenReturn(java.util.List.of(revoked));
        when(users.findByUuid("moderator")).thenReturn(Optional.of(
                new dev.rafex.insightbloom.users.domain.model.User("moderator", "mod", "Moderador", "mod@example.test", UserRole.MODERATOR)));

        final var useCase = new TicketUseCase(conferences, eventTypes, tickets,
                mock(ConferenceMembershipRepository.class), mock(EmailPort.class), "",
                mock(ReservationRepository.class), null, users);

        final var summary = useCase.listManagement(conference.getUuid());

        assertEquals("Moderador", summary.ticketActors().get("moderator").displayName());
        assertTrue(summary.claimedUsers().isEmpty());
    }

    @Test
    void operationalTicketCannotBeRevokedOrReleaseCapacity() {
        final ConferenceRepository conferences = mock(ConferenceRepository.class);
        final EventTypeRepository eventTypes = mock(EventTypeRepository.class);
        final TicketRepository tickets = mock(TicketRepository.class);
        final var conference = new dev.rafex.insightbloom.users.domain.model.Conference("event", "Evento", "owner");
        conference.setCapacity(2);
        final Ticket operational = Ticket.operational(conference.getUuid(), "owner", "owner");
        when(conferences.findByUuid(conference.getUuid())).thenReturn(Optional.of(conference));
        when(eventTypes.findByKey("conference")).thenReturn(Optional.of(
                new EventType("conference", "Conferencia", null, Set.of(EventCapability.TICKETING_GENERAL))));
        when(tickets.findByUuid(operational.getUuid())).thenReturn(Optional.of(operational));

        final var useCase = new TicketUseCase(conferences, eventTypes, tickets,
                mock(ConferenceMembershipRepository.class), mock(EmailPort.class), "", mock(ReservationRepository.class));

        final IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> useCase.revoke(conference.getUuid(), operational.getUuid(), "owner"));

        assertEquals("operational_ticket_protected", error.getMessage());
        verify(tickets, never()).revoke(anyString(), anyString(), anyString());
        verify(conferences, never()).decrementReservedCount(anyString());
    }

    @Test
    void revokedUserLosesAccessButCanClaimAnotherTicket() {
        final ConferenceRepository conferences = mock(ConferenceRepository.class);
        final EventTypeRepository eventTypes = mock(EventTypeRepository.class);
        final TicketRepository tickets = mock(TicketRepository.class);
        final ConferenceMembershipRepository memberships = mock(ConferenceMembershipRepository.class);
        final ReservationRepository reservations = mock(ReservationRepository.class);
        final var conference = new dev.rafex.insightbloom.users.domain.model.Conference(
                "event", "Evento", "owner");
        final var revokedTicket = new Ticket(conference.getUuid(), "owner", null, null);
        final var replacementTicket = new Ticket(conference.getUuid(), "owner", null, null);
        when(conferences.findByUuid(conference.getUuid())).thenReturn(Optional.of(conference));
        when(eventTypes.findByKey("conference")).thenReturn(Optional.of(
                new EventType("conference", "Conferencia", null, Set.of(EventCapability.TICKETING_GENERAL))));
        when(tickets.findByUuid(revokedTicket.getUuid())).thenReturn(Optional.of(revokedTicket));
        when(tickets.revoke(eq(revokedTicket.getUuid()), eq("moderator"), anyString())).thenReturn(true);

        final var useCase = new TicketUseCase(conferences, eventTypes, tickets, memberships,
                mock(EmailPort.class), "", reservations);

        useCase.revoke(conference.getUuid(), revokedTicket.getUuid(), "moderator");
        when(tickets.findByConferenceAndUser(conference.getUuid(), "user")).thenReturn(Optional.empty());
        assertFalse(useCase.hasAccess(conference, "user"));

        when(tickets.findByCode(conference.getUuid(), replacementTicket.getTicketCode()))
                .thenReturn(Optional.of(replacementTicket));
        when(tickets.claim(eq(replacementTicket.getUuid()), eq("user"), anyString())).thenReturn(true);
        when(tickets.findByUuid(replacementTicket.getUuid())).thenReturn(Optional.of(replacementTicket));
        when(memberships.exists("user", conference.getUuid())).thenReturn(false);

        assertSame(replacementTicket, useCase.claim(conference.getUuid(), replacementTicket.getTicketCode(), "user"));
        when(tickets.findByConferenceAndUser(conference.getUuid(), "user"))
                .thenReturn(Optional.of(replacementTicket));
        assertTrue(useCase.hasAccess(conference, "user"));
        verify(tickets).revoke(eq(revokedTicket.getUuid()), eq("moderator"), anyString());
        verify(tickets).claim(eq(replacementTicket.getUuid()), eq("user"), anyString());
    }

    @Test
    void expiresTicketsFiveHoursAfterEventStart() {
        final ConferenceRepository conferences = mock(ConferenceRepository.class);
        final EventTypeRepository eventTypes = mock(EventTypeRepository.class);
        final TicketRepository tickets = mock(TicketRepository.class);
        final ReservationRepository reservations = mock(ReservationRepository.class);
        final TimezoneRepository timezones = mock(TimezoneRepository.class);
        final var conference = new dev.rafex.insightbloom.users.domain.model.Conference("event", "Evento", "owner");
        final Instant eventStart = Instant.now().minusSeconds(6 * 60 * 60);
        final var eventStartLocal = eventStart.atOffset(ZoneOffset.UTC);
        conference.setEventDate(LocalDate.from(eventStartLocal).toString());
        conference.setStartTime(LocalTime.from(eventStartLocal).toString());
        conference.setTimezoneId(1);
        when(conferences.findByUuid(conference.getUuid())).thenReturn(Optional.of(conference));
        when(eventTypes.findByKey("conference")).thenReturn(Optional.of(
                new EventType("conference", "Conferencia", null, Set.of(EventCapability.TICKETING_GENERAL))));
        when(timezones.findById(1)).thenReturn(Optional.of(new dev.rafex.insightbloom.users.domain.model.Timezone(
                1, "UTC", "UTC", 0, true)));

        final var useCase = new TicketUseCase(conferences, eventTypes, tickets,
                mock(ConferenceMembershipRepository.class), mock(EmailPort.class), "", reservations, timezones);

        assertFalse(useCase.hasAccess(conference, "user"));
        when(conferences.findAll()).thenReturn(java.util.List.of(conference));
        assertEquals(1, useCase.expireTickets(Instant.now()));
        verify(tickets, times(2)).expireByConference(eq(conference.getUuid()), anyString());
    }
}
