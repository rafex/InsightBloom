package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.*;
import dev.rafex.insightbloom.users.domain.ports.EmailPort;
import dev.rafex.insightbloom.users.domain.ports.OtpCodeRepository;
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
        final EmailPort emailPort = Mockito.mock(EmailPort.class);
        Mockito.when(userRepo.findByUsername("admin")).thenReturn(Optional.of(otpUser()));
        Mockito.when(emailPort.isEnabled()).thenReturn(true);
        Mockito.when(otpRepo.countSince(Mockito.eq("admin@test.com"), Mockito.any())).thenReturn(0);

        new RequestLoginOtpUseCase(userRepo, otpRepo, emailPort)
                .execute(new RequestLoginOtpUseCase.Request("admin"));

        Mockito.verify(otpRepo).save(Mockito.any(OtpCode.class));
        Mockito.verify(emailPort).sendHtml(Mockito.eq("admin@test.com"), Mockito.anyString(), Mockito.anyString());
    }

    @Test
    void request_unknownIdentifier_neverThrowsNorSends() {
        final UserRepository userRepo = Mockito.mock(UserRepository.class);
        final OtpCodeRepository otpRepo = Mockito.mock(OtpCodeRepository.class);
        final EmailPort emailPort = Mockito.mock(EmailPort.class);
        Mockito.when(userRepo.findByUsername("nobody")).thenReturn(Optional.empty());
        Mockito.when(userRepo.findByEmail("nobody")).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> new RequestLoginOtpUseCase(userRepo, otpRepo, emailPort)
                .execute(new RequestLoginOtpUseCase.Request("nobody")));
        Mockito.verifyNoInteractions(emailPort);
    }

    @Test
    void request_accountStillOnPassword_doesNotSend() {
        final UserRepository userRepo = Mockito.mock(UserRepository.class);
        final OtpCodeRepository otpRepo = Mockito.mock(OtpCodeRepository.class);
        final EmailPort emailPort = Mockito.mock(EmailPort.class);
        final User passwordUser = new User("1", "uuid-2", "regular", "Regular", "regular@test.com", null,
                java.util.List.of(), false, false, java.util.Set.of(UserRole.ORGANIZER), UserStatus.ACTIVE,
                "hash", Instant.now(), Instant.now());
        Mockito.when(userRepo.findByUsername("regular")).thenReturn(Optional.of(passwordUser));

        new RequestLoginOtpUseCase(userRepo, otpRepo, emailPort)
                .execute(new RequestLoginOtpUseCase.Request("regular"));

        Mockito.verifyNoInteractions(emailPort);
        Mockito.verify(otpRepo, Mockito.never()).save(Mockito.any());
    }

    @Test
    void request_rateLimitReached_doesNotSendAnotherCode() {
        final UserRepository userRepo = Mockito.mock(UserRepository.class);
        final OtpCodeRepository otpRepo = Mockito.mock(OtpCodeRepository.class);
        final EmailPort emailPort = Mockito.mock(EmailPort.class);
        Mockito.when(userRepo.findByUsername("admin")).thenReturn(Optional.of(otpUser()));
        Mockito.when(emailPort.isEnabled()).thenReturn(true);
        // Ya se enviaron 3 codigos en la ultima hora -- el limite de envio es 3.
        Mockito.when(otpRepo.countSince(Mockito.eq("admin@test.com"), Mockito.any())).thenReturn(3);

        new RequestLoginOtpUseCase(userRepo, otpRepo, emailPort)
                .execute(new RequestLoginOtpUseCase.Request("admin"));

        Mockito.verify(emailPort, Mockito.never()).sendHtml(Mockito.anyString(), Mockito.anyString(), Mockito.anyString());
        Mockito.verify(otpRepo, Mockito.never()).save(Mockito.any());
    }

    @Test
    void request_generatesSixDigitCode() {
        final UserRepository userRepo = Mockito.mock(UserRepository.class);
        final OtpCodeRepository otpRepo = Mockito.mock(OtpCodeRepository.class);
        final EmailPort emailPort = Mockito.mock(EmailPort.class);
        Mockito.when(userRepo.findByUsername("admin")).thenReturn(Optional.of(otpUser()));
        Mockito.when(emailPort.isEnabled()).thenReturn(true);
        Mockito.when(otpRepo.countSince(Mockito.eq("admin@test.com"), Mockito.any())).thenReturn(0);

        new RequestLoginOtpUseCase(userRepo, otpRepo, emailPort)
                .execute(new RequestLoginOtpUseCase.Request("admin"));

        final ArgumentCaptor<OtpCode> captor = ArgumentCaptor.forClass(OtpCode.class);
        Mockito.verify(otpRepo).save(captor.capture());
        assertEquals(6, captor.getValue().getCode().length());
        assertTrue(captor.getValue().getCode().matches("[0-9]{6}"));
        assertEquals(OtpChannel.EMAIL, captor.getValue().getChannel());
    }

    @Test
    void request_blankIdentifier_throws() {
        final UserRepository userRepo = Mockito.mock(UserRepository.class);
        final OtpCodeRepository otpRepo = Mockito.mock(OtpCodeRepository.class);
        final EmailPort emailPort = Mockito.mock(EmailPort.class);
        assertThrows(IllegalArgumentException.class, () -> new RequestLoginOtpUseCase(userRepo, otpRepo, emailPort)
                .execute(new RequestLoginOtpUseCase.Request(" ")));
    }
}
