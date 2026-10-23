package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.Ticket;
import dev.rafex.insightbloom.users.domain.ports.OrganizerTicketSignatureRepository;
import dev.rafex.insightbloom.users.domain.ports.TicketDeviceRepository;
import dev.rafex.insightbloom.users.domain.ports.TicketRepository;
import dev.rafex.insightbloom.users.domain.services.CoseSign1Ed25519;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Base64;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OrganizerTicketSignatureUseCaseTest {
    @Test
    void verifiesEventProofAndOnlyAcceptsSignatureForTheTicketClaim() throws Exception {
        final var pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        final String publicSpki = Base64.getUrlEncoder().withoutPadding().encodeToString(pair.getPublic().getEncoded());
        final var repo = mock(OrganizerTicketSignatureRepository.class);
        final var tickets = mock(TicketRepository.class);
        final var useCase = new OrganizerTicketSignatureUseCase(repo, tickets, mock(TicketDeviceRepository.class));
        final String keyId = "event-key-1";
        final String challenge = "insightbloom-event-key-proof-v1|event-1|" + keyId;
        final var proofSigner = Signature.getInstance("Ed25519"); proofSigner.initSign(pair.getPrivate());
        proofSigner.update(challenge.getBytes(StandardCharsets.UTF_8));
        final String proof = Base64.getUrlEncoder().withoutPadding().encodeToString(proofSigner.sign());

        when(repo.activeKey("event-1")).thenReturn(Optional.empty(), Optional.of(new OrganizerTicketSignatureRepository.EventKey(
                "event-1", keyId, publicSpki, "ACTIVE", java.time.Instant.now())));
        useCase.registerKey("event-1", keyId, publicSpki, proof, "owner");

        final Ticket ticket = new Ticket("event-1", "owner", "attendee@example.test", null, 1, "EMAIL_OTP");
        ticket.markPendingSignature();
        when(tickets.findByUuid(ticket.getUuid())).thenReturn(Optional.of(ticket));
        when(repo.saveTicketSignature(eq("event-1"), eq(ticket.getUuid()), eq(keyId), anyString(), eq("owner"), any()))
                .thenReturn(true);
        final byte[] payload = OrganizerTicketSignatureUseCase.canonicalTicketPayload(ticket).getBytes(StandardCharsets.UTF_8);
        final var signer = Signature.getInstance("Ed25519"); signer.initSign(pair.getPrivate());
        signer.update(CoseSign1Ed25519.signatureInput(payload));
        final String cose = Base64.getUrlEncoder().withoutPadding().encodeToString(CoseSign1Ed25519.encode(
                keyId.getBytes(StandardCharsets.UTF_8), payload, signer.sign()));

        assertTrue(useCase.acceptTicketSignature("event-1", ticket.getUuid(), cose, "owner"));
        verify(repo).saveTicketSignature(eq("event-1"), eq(ticket.getUuid()), eq(keyId), eq(cose), eq("owner"), any());
        final String tampered = Base64.getUrlEncoder().withoutPadding().encodeToString(CoseSign1Ed25519.encode(
                keyId.getBytes(StandardCharsets.UTF_8), "wrong-event".getBytes(StandardCharsets.UTF_8), new byte[64]));
        assertEquals("ticket_signature_payload_mismatch", assertThrows(IllegalArgumentException.class,
                () -> useCase.acceptTicketSignature("event-1", ticket.getUuid(), tampered, "owner")).getMessage());
    }
}
