package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.*;
import dev.rafex.insightbloom.users.domain.ports.EmailPort;
import dev.rafex.insightbloom.users.domain.ports.OtpCodeRepository;
import dev.rafex.insightbloom.users.domain.ports.OtpRequestAuditPort;
import dev.rafex.insightbloom.users.domain.ports.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class RequestLoginOtpUseCaseTest {

    private User otpUser() {
        final User user = new User("1", "uuid-1", "admin", "Admin", "admin@test.com", null, java.util.List.of(),
                false, false, java.util.Set.of(UserRole.ORGANIZER), UserStatus.ACTIVE,
                "irrelevant-hash", Instant.now(), Instant.now());
        user.setAuthMethod(AuthMethod.OTP_EMAIL);
        return user;
    }

    @Test
    void request_eligibleAccount_sendsEmailAndSavesCode() {
        final UserRepository userRepo = Mockito.mock(UserRepository.class);
        final OtpCodeRepository otpRepo = Mockito.mock(OtpCodeRepository.class);
        final OtpRequestAuditPort auditRepo = Mockito.mock(OtpRequestAuditPort.class);
        final EmailPort emailPort = Mockito.mock(EmailPort.class);
        Mockito.when(userRepo.findByUsername("admin")).thenReturn(Optional.of(otpUser()));
        Mockito.when(emailPort.isEnabled()).thenReturn(true);
        Mockito.when(otpRepo.countSince(Mockito.eq("admin@test.com"), Mockito.any())).thenReturn(0);

        new RequestLoginOtpUseCase(userRepo, otpRepo, auditRepo, emailPort)
                .execute(new RequestLoginOtpUseCase.Request("admin", "203.0.113.10", "test-agent"));

        Mockito.verify(otpRepo).savePending(Mockito.any(OtpCode.class));
        final ArgumentCaptor<OtpCode> codeCaptor = ArgumentCaptor.forClass(OtpCode.class);
        Mockito.verify(otpRepo).savePending(codeCaptor.capture());
        Mockito.verify(otpRepo).markDelivered(codeCaptor.getValue().getUuid());
        Mockito.verify(emailPort).sendHtml(Mockito.eq("admin@test.com"), Mockito.anyString(), Mockito.anyString());
        final ArgumentCaptor<dev.rafex.insightbloom.users.domain.model.OtpRequestAudit> auditCaptor =
                ArgumentCaptor.forClass(dev.rafex.insightbloom.users.domain.model.OtpRequestAudit.class);
        Mockito.verify(auditRepo).save(auditCaptor.capture());
        assertEquals("smtp_accepted", auditCaptor.getValue().outcome());
        assertEquals("203.0.113.10", auditCaptor.getValue().clientIp());
        assertEquals("uuid-1", auditCaptor.getValue().accountUuid());
    }

    @Test
    void request_unknownIdentifier_neverThrowsNorSends() {
        final UserRepository userRepo = Mockito.mock(UserRepository.class);
        final OtpCodeRepository otpRepo = Mockito.mock(OtpCodeRepository.class);
        final OtpRequestAuditPort auditRepo = Mockito.mock(OtpRequestAuditPort.class);
        final EmailPort emailPort = Mockito.mock(EmailPort.class);
        Mockito.when(userRepo.findByUsername("nobody")).thenReturn(Optional.empty());
        Mockito.when(userRepo.findByEmail("nobody")).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> new RequestLoginOtpUseCase(userRepo, otpRepo, auditRepo, emailPort)
                .execute(new RequestLoginOtpUseCase.Request("nobody", "203.0.113.8", "agent")));
        Mockito.verifyNoInteractions(emailPort);
        Mockito.verify(otpRepo, Mockito.never()).savePending(Mockito.any());
        final ArgumentCaptor<dev.rafex.insightbloom.users.domain.model.OtpRequestAudit> captor =
                ArgumentCaptor.forClass(dev.rafex.insightbloom.users.domain.model.OtpRequestAudit.class);
        Mockito.verify(auditRepo).save(captor.capture());
        assertEquals("account_not_found", captor.getValue().outcome());
        assertNull(captor.getValue().accountUuid());
    }

    @Test
    void request_accountStillOnPassword_doesNotSend() {
        final UserRepository userRepo = Mockito.mock(UserRepository.class);
        final OtpCodeRepository otpRepo = Mockito.mock(OtpCodeRepository.class);
        final OtpRequestAuditPort auditRepo = Mockito.mock(OtpRequestAuditPort.class);
        final EmailPort emailPort = Mockito.mock(EmailPort.class);
        final User passwordUser = new User("1", "uuid-2", "regular", "Regular", "regular@test.com", null,
                java.util.List.of(), false, false, java.util.Set.of(UserRole.ORGANIZER), UserStatus.ACTIVE,
                "hash", Instant.now(), Instant.now());
        Mockito.when(userRepo.findByUsername("regular")).thenReturn(Optional.of(passwordUser));

        new RequestLoginOtpUseCase(userRepo, otpRepo, auditRepo, emailPort)
                .execute(new RequestLoginOtpUseCase.Request("regular"));

        Mockito.verifyNoInteractions(emailPort);
        Mockito.verify(otpRepo, Mockito.never()).savePending(Mockito.any());
        final ArgumentCaptor<dev.rafex.insightbloom.users.domain.model.OtpRequestAudit> captor =
                ArgumentCaptor.forClass(dev.rafex.insightbloom.users.domain.model.OtpRequestAudit.class);
        Mockito.verify(auditRepo).save(captor.capture());
        assertEquals("otp_not_enabled", captor.getValue().outcome());
    }

    @Test
    void request_rateLimitReached_doesNotSendAnotherCode() {
        final UserRepository userRepo = Mockito.mock(UserRepository.class);
        final OtpCodeRepository otpRepo = Mockito.mock(OtpCodeRepository.class);
        final OtpRequestAuditPort auditRepo = Mockito.mock(OtpRequestAuditPort.class);
        final EmailPort emailPort = Mockito.mock(EmailPort.class);
        Mockito.when(userRepo.findByUsername("admin")).thenReturn(Optional.of(otpUser()));
        Mockito.when(emailPort.isEnabled()).thenReturn(true);
        // Ya se enviaron 3 codigos en la ultima hora -- el limite de envio es 3.
        Mockito.when(otpRepo.countSince(Mockito.eq("admin@test.com"), Mockito.any())).thenReturn(3);

        new RequestLoginOtpUseCase(userRepo, otpRepo, auditRepo, emailPort)
                .execute(new RequestLoginOtpUseCase.Request("admin"));

        Mockito.verify(emailPort, Mockito.never()).sendHtml(Mockito.anyString(), Mockito.anyString(), Mockito.anyString());
        Mockito.verify(otpRepo, Mockito.never()).savePending(Mockito.any());
    }

    @Test
    void request_generatesSixDigitCode() {
        final UserRepository userRepo = Mockito.mock(UserRepository.class);
        final OtpCodeRepository otpRepo = Mockito.mock(OtpCodeRepository.class);
        final OtpRequestAuditPort auditRepo = Mockito.mock(OtpRequestAuditPort.class);
        final EmailPort emailPort = Mockito.mock(EmailPort.class);
        Mockito.when(userRepo.findByUsername("admin")).thenReturn(Optional.of(otpUser()));
        Mockito.when(emailPort.isEnabled()).thenReturn(true);
        Mockito.when(otpRepo.countSince(Mockito.eq("admin@test.com"), Mockito.any())).thenReturn(0);

        new RequestLoginOtpUseCase(userRepo, otpRepo, auditRepo, emailPort)
                .execute(new RequestLoginOtpUseCase.Request("admin"));

        final ArgumentCaptor<OtpCode> captor = ArgumentCaptor.forClass(OtpCode.class);
        Mockito.verify(otpRepo).savePending(captor.capture());
        assertEquals(6, captor.getValue().getCode().length());
        assertTrue(captor.getValue().getCode().matches("[0-9]{6}"));
        assertEquals(OtpChannel.EMAIL, captor.getValue().getChannel());
    }

    @Test
    void request_smtpFailureInvalidatesPendingCodeAndDoesNotCountAsSuccessfulSend() {
        final UserRepository userRepo = Mockito.mock(UserRepository.class);
        final OtpCodeRepository otpRepo = Mockito.mock(OtpCodeRepository.class);
        final OtpRequestAuditPort auditRepo = Mockito.mock(OtpRequestAuditPort.class);
        final EmailPort emailPort = Mockito.mock(EmailPort.class);
        Mockito.when(userRepo.findByUsername("admin")).thenReturn(Optional.of(otpUser()));
        Mockito.when(emailPort.isEnabled()).thenReturn(true);
        Mockito.when(otpRepo.countSince(Mockito.eq("admin@test.com"), Mockito.any())).thenReturn(0);
        Mockito.doThrow(new RuntimeException("smtp rejected")).when(emailPort)
                .sendHtml(Mockito.anyString(), Mockito.anyString(), Mockito.anyString());

        assertDoesNotThrow(() -> new RequestLoginOtpUseCase(userRepo, otpRepo, auditRepo, emailPort)
                .execute(new RequestLoginOtpUseCase.Request("admin")));

        final ArgumentCaptor<OtpCode> codeCaptor = ArgumentCaptor.forClass(OtpCode.class);
        Mockito.verify(otpRepo).savePending(codeCaptor.capture());
        Mockito.verify(otpRepo).markConsumed(codeCaptor.getValue().getUuid());
        Mockito.verify(otpRepo, Mockito.never()).markDelivered(Mockito.anyString());
        final ArgumentCaptor<dev.rafex.insightbloom.users.domain.model.OtpRequestAudit> auditCaptor =
                ArgumentCaptor.forClass(dev.rafex.insightbloom.users.domain.model.OtpRequestAudit.class);
        Mockito.verify(auditRepo).save(auditCaptor.capture());
        assertEquals("smtp_failed", auditCaptor.getValue().outcome());
    }

    @Test
    void auditStorageFailureDoesNotDisableRateLimitOrChangeRequestOutcome() {
        final UserRepository userRepo = Mockito.mock(UserRepository.class);
        final OtpCodeRepository otpRepo = Mockito.mock(OtpCodeRepository.class);
        final OtpRequestAuditPort auditRepo = Mockito.mock(OtpRequestAuditPort.class);
        final EmailPort emailPort = Mockito.mock(EmailPort.class);
        Mockito.when(userRepo.findByUsername("admin")).thenReturn(Optional.of(otpUser()));
        Mockito.when(emailPort.isEnabled()).thenReturn(true);
        Mockito.when(otpRepo.countSince(Mockito.eq("admin@test.com"), Mockito.any())).thenReturn(3);
        Mockito.doThrow(new RuntimeException("audit unavailable")).when(auditRepo).save(Mockito.any());

        assertDoesNotThrow(() -> new RequestLoginOtpUseCase(userRepo, otpRepo, auditRepo, emailPort)
                .execute(new RequestLoginOtpUseCase.Request("admin")));

        Mockito.verify(emailPort, Mockito.never()).sendHtml(Mockito.anyString(), Mockito.anyString(), Mockito.anyString());
        Mockito.verify(auditRepo).save(Mockito.argThat(row -> row.outcome().equals("rate_limited")));
    }

    @Test
    void request_blankIdentifier_throws() {
        final UserRepository userRepo = Mockito.mock(UserRepository.class);
        final OtpCodeRepository otpRepo = Mockito.mock(OtpCodeRepository.class);
        final OtpRequestAuditPort auditRepo = Mockito.mock(OtpRequestAuditPort.class);
        final EmailPort emailPort = Mockito.mock(EmailPort.class);
        assertThrows(IllegalArgumentException.class, () -> new RequestLoginOtpUseCase(userRepo, otpRepo, auditRepo, emailPort)
                .execute(new RequestLoginOtpUseCase.Request(" ")));
    }
}
