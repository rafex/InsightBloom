package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.OrganizerInvitation;
import dev.rafex.insightbloom.users.domain.model.OrganizerInviteSettings;
import dev.rafex.insightbloom.users.domain.ports.EmailPort;
import dev.rafex.insightbloom.users.domain.ports.OrganizerInvitationRepository;
import dev.rafex.insightbloom.users.domain.ports.UserRepository;
import dev.rafex.insightbloom.users.domain.services.TokenService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;

class OrganizerInvitationUseCaseTest {
    private OrganizerInvitationUseCase fixture(OrganizerInvitationRepository repository, EmailPort emailPort) {
        Mockito.when(repository.settings()).thenReturn(new OrganizerInviteSettings(false, 7, 3));
        return new OrganizerInvitationUseCase(repository, Mockito.mock(UserRepository.class), emailPort,
                Mockito.mock(TokenService.class), "https://insightbloom.example");
    }

    @Test
    void createsNormalizedEmailInvitationAndEmailsOnlyHashBackedOpaqueToken() throws Exception {
        OrganizerInvitationRepository repository = Mockito.mock(OrganizerInvitationRepository.class);
        EmailPort emailPort = Mockito.mock(EmailPort.class);
        Mockito.when(emailPort.isEnabled()).thenReturn(true);
        Instant now = Instant.now();
        Mockito.when(repository.createInvitation(anyString(), anyString(), anyString(), isNull(), any(), any(), anyString()))
                .thenReturn(new OrganizerInvitation("invite-1", "person@example.com", "PENDING", "admin", null,
                        now, now.plusSeconds(604800), null, null, null));
        Mockito.when(repository.findInvitation("invite-1"))
                .thenReturn(java.util.Optional.of(new OrganizerInvitation("invite-1", "person@example.com", "PENDING", "admin", null,
                        now, now.plusSeconds(604800), now, null, null)));

        OrganizerInvitationUseCase useCase = fixture(repository, emailPort);
        useCase.createInvitation(" Person@Example.COM ", "admin", "ip-hash");

        ArgumentCaptor<String> email = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> tokenHash = ArgumentCaptor.forClass(String.class);
        Mockito.verify(repository).createInvitation(email.capture(), tokenHash.capture(), eq("admin"), isNull(), any(), any(), eq("ip-hash"));
        assertEquals("person@example.com", email.getValue());
        assertTrue(tokenHash.getValue().matches("[a-f0-9]{64}"));
        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        Mockito.verify(emailPort).sendHtml(eq("person@example.com"), anyString(), html.capture());
        String rawToken = html.getValue().replaceAll("(?s).*#token=([A-Za-z0-9_-]{43}).*", "$1");
        String expectedHash = java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        assertEquals(expectedHash, tokenHash.getValue());
    }

    @Test
    void recordsSafeDeliveryFailureAndAllowsAdminToRetry() {
        OrganizerInvitationRepository repository = Mockito.mock(OrganizerInvitationRepository.class);
        EmailPort emailPort = Mockito.mock(EmailPort.class);
        Mockito.when(emailPort.isEnabled()).thenReturn(true);
        Mockito.doThrow(new RuntimeException("smtp details include private transport data"))
                .when(emailPort).sendHtml(anyString(), anyString(), anyString());
        Instant now = Instant.now();
        Mockito.when(repository.createInvitation(anyString(), anyString(), anyString(), isNull(), any(), any(), anyString()))
                .thenReturn(new OrganizerInvitation("invite-2", "person@example.com", "PENDING", "admin", null,
                        now, now.plusSeconds(604800), null, null, null));

        fixture(repository, emailPort).createInvitation("person@example.com", "admin", "ip-hash");

        Mockito.verify(repository).markDelivery(eq("invite-2"), eq(false), eq("email_delivery_failed"), any());
        Mockito.verify(repository, Mockito.never()).markDelivery(anyString(), eq(false), contains("smtp"), any());
    }
}
