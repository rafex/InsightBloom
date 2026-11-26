package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.Conference;
import dev.rafex.insightbloom.users.domain.model.EventCapability;
import dev.rafex.insightbloom.users.domain.model.EventType;
import dev.rafex.insightbloom.users.domain.model.Ticket;
import dev.rafex.insightbloom.users.domain.model.TicketStatus;
import dev.rafex.insightbloom.users.domain.ports.*;
import dev.rafex.insightbloom.users.domain.services.CoseSign1Ed25519;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PlatformTicketIssuanceTest {
    @Test
    void automaticallySignsFinalizesAndReturnsSignedQr() throws Exception {
        final Conference event = new Conference("event", "Evento", "owner");
        event.setTicketSigningMode("PLATFORM");
        event.setCapacity(2);
        final ConferenceRepository conferences = mock(ConferenceRepository.class);
        final EventTypeRepository eventTypes = mock(EventTypeRepository.class);
        final TicketRepository tickets = mock(TicketRepository.class);
        final OrganizerTicketSignatureRepository signatures = mock(OrganizerTicketSignatureRepository.class);
        final KeyPair keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        final String keyId = "platform-test-key";
        final String publicKey = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(keyPair.getPublic().getEncoded());
        final AtomicReference<Ticket> pending = new AtomicReference<>();
        final AtomicReference<Ticket> issued = new AtomicReference<>();
        final AtomicReference<String> persistedCose = new AtomicReference<>();
        when(conferences.findByUuid(event.getUuid())).thenReturn(Optional.of(event));
        when(conferences.tryIncrementReservedCount(event.getUuid())).thenReturn(true);
        when(eventTypes.findByKey("conference")).thenReturn(Optional.of(new EventType(
                "conference", "Conference", null, Set.of(EventCapability.TICKETING_GENERAL))));
        when(signatures.activeKey(event.getUuid())).thenReturn(Optional.empty());
        when(signatures.key(event.getUuid(), keyId)).thenReturn(Optional.of(new OrganizerTicketSignatureRepository.EventKey(
                event.getUuid(), keyId, publicKey, "ACTIVE", Instant.now())));
        when(signatures.saveTicketSignature(eq(event.getUuid()), anyString(), eq(keyId), anyString(), anyString(), any()))
                .thenAnswer(call -> { persistedCose.set(call.getArgument(3)); return true; });
        when(signatures.ticketSignature(eq(event.getUuid()), anyString())).thenAnswer(call -> Optional.ofNullable(persistedCose.get()));
        doAnswer(call -> { pending.set(call.getArgument(0)); return null; }).when(tickets).insert(any(Ticket.class));
        when(tickets.findByUuid(anyString())).thenAnswer(call -> Optional.of(issued.get() != null ? issued.get() : pending.get()));
        when(tickets.finalizeSignature(anyString())).thenAnswer(call -> {
            final Ticket t = pending.get();
            issued.set(new Ticket(t.getUuid(), t.getConferenceUuid(), t.storedTicketCode(), t.getIssuedByUserUuid(),
                    t.getRecipientEmail(), t.getSeatUuid(), TicketStatus.ISSUED, t.getClaimedByUserUuid(),
                    t.getIssuedAt(), t.getClaimedAt(), t.getCheckedInAt(), t.getRevokedByUserUuid(), t.getRevokedAt(),
                    false, t.getDeviceMaxDevices(), t.getDeviceEnrollmentMethod()));
            return true;
        });

        final var signatureUseCase = new OrganizerTicketSignatureUseCase(signatures, tickets, mock(TicketDeviceRepository.class));
        final var useCase = new TicketUseCase(conferences, eventTypes, tickets, mock(ConferenceMembershipRepository.class),
                mock(EmailPort.class), "", mock(ReservationRepository.class));
        useCase.configureOrganizerTicketSignatures(signatureUseCase);
        useCase.configurePlatformTicketSigner((conferenceUuid, canonicalPayload) -> {
            try {
                final byte[] payload = canonicalPayload.getBytes(StandardCharsets.UTF_8);
                final Signature signer = Signature.getInstance("Ed25519");
                signer.initSign(keyPair.getPrivate());
                signer.update(CoseSign1Ed25519.signatureInput(payload));
                return new PlatformTicketSigner.Signed(keyId, publicKey,
                        Base64.getUrlEncoder().withoutPadding().encodeToString(signer.sign()));
            } catch (Exception e) { throw new IllegalStateException(e); }
        });

        final Ticket ticket = useCase.issue(event.getUuid(), "owner", "guest@example.test", null);

        assertEquals(TicketStatus.ISSUED, ticket.getStatus());
        assertNotNull(ticket.getSignedQrCode());
        assertTrue(ticket.getSignedQrCode().contains(persistedCose.get()));
        assertTrue(signatureUseCase.verifyTicketSignature(event.getUuid(), ticket, persistedCose.get()));
        verify(signatures).saveTicketSignature(eq(event.getUuid()), eq(ticket.getUuid()), eq(keyId), anyString(),
                eq("insightbloom-platform"), any());
    }

    @Test
    void signerFailureRevokesNewTicketAndReleasesCapacity() {
        final Conference event = new Conference("event", "Evento", "owner");
        event.setTicketSigningMode("PLATFORM");
        event.setCapacity(2);
        final ConferenceRepository conferences = mock(ConferenceRepository.class);
        final EventTypeRepository eventTypes = mock(EventTypeRepository.class);
        final TicketRepository tickets = mock(TicketRepository.class);
        when(conferences.findByUuid(event.getUuid())).thenReturn(Optional.of(event));
        when(conferences.tryIncrementReservedCount(event.getUuid())).thenReturn(true);
        when(eventTypes.findByKey("conference")).thenReturn(Optional.of(new EventType(
                "conference", "Conference", null, Set.of(EventCapability.TICKETING_GENERAL))));
        final var useCase = new TicketUseCase(conferences, eventTypes, tickets,
                mock(ConferenceMembershipRepository.class), mock(EmailPort.class), "", mock(ReservationRepository.class));
        useCase.configurePlatformTicketSigner((conferenceUuid, payload) -> { throw new IllegalStateException("signer_down"); });

        assertEquals("signer_down", assertThrows(IllegalStateException.class,
                () -> useCase.issue(event.getUuid(), "owner", "guest@example.test", null)).getMessage());
        verify(tickets).revoke(anyString(), eq("owner"), anyString());
        verify(conferences).decrementReservedCount(event.getUuid());
    }
}
