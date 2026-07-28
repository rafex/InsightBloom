package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.*;
import dev.rafex.insightbloom.users.domain.ports.OtpCodeRepository;
import dev.rafex.insightbloom.users.domain.ports.TokenRepository;
import dev.rafex.insightbloom.users.domain.ports.UserRepository;
import dev.rafex.insightbloom.users.domain.services.TokenService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class VerifyLoginOtpUseCaseTest {

    private User otpUser() {
        final User user = new User("1", "uuid-1", "admin", "Admin", "admin@test.com", null, java.util.List.of(),
                false, false, java.util.Set.of(UserRole.ORGANIZER), UserStatus.ACTIVE,
                "irrelevant-hash", Instant.now(), Instant.now());
        user.setAuthMethod(AuthMethod.OTP_EMAIL);
        return user;
    }

    private VerifyLoginOtpUseCase useCase(final UserRepository userRepo, final OtpCodeRepository otpRepo) {
        final TokenRepository tokenRepo = Mockito.mock(TokenRepository.class);
        Mockito.when(tokenRepo.findActiveByUser(Mockito.anyString())).thenReturn(List.of());
        Mockito.when(tokenRepo.findActiveByFingerprint(Mockito.anyString())).thenReturn(List.of());
        return new VerifyLoginOtpUseCase(userRepo, otpRepo, new TokenService(tokenRepo));
    }

    @Test
    void verify_correctCode_issuesToken() {
        final UserRepository userRepo = Mockito.mock(UserRepository.class);
        final OtpCodeRepository otpRepo = Mockito.mock(OtpCodeRepository.class);
        Mockito.when(userRepo.findByUsername("admin")).thenReturn(Optional.of(otpUser()));
        final OtpCode code = new OtpCode("admin@test.com", OtpChannel.EMAIL, "123456", Instant.now().plusSeconds(60));
        Mockito.when(otpRepo.findLatestActive("admin@test.com")).thenReturn(Optional.of(code));

        final var result = useCase(userRepo, otpRepo)
                .execute(new VerifyLoginOtpUseCase.Request("admin", "123456", null));

        assertNotNull(result.token());
        assertEquals("uuid-1", result.userUuid());
        Mockito.verify(otpRepo).markConsumed(code.getUuid());
    }

    @Test
    void verify_wrongCode_incrementsFailedAttemptsAndThrows() {
        final UserRepository userRepo = Mockito.mock(UserRepository.class);
        final OtpCodeRepository otpRepo = Mockito.mock(OtpCodeRepository.class);
        Mockito.when(userRepo.findByUsername("admin")).thenReturn(Optional.of(otpUser()));
        final OtpCode code = new OtpCode("admin@test.com", OtpChannel.EMAIL, "123456", Instant.now().plusSeconds(60));
        Mockito.when(otpRepo.findLatestActive("admin@test.com")).thenReturn(Optional.of(code));

        assertThrows(IllegalArgumentException.class, () -> useCase(userRepo, otpRepo)
                .execute(new VerifyLoginOtpUseCase.Request("admin", "000000", null)));
        Mockito.verify(otpRepo).incrementFailedAttempts(code.getUuid());
        Mockito.verify(otpRepo, Mockito.never()).markConsumed(Mockito.anyString());
    }

    @Test
    void verify_tooManyFailedAttempts_locksOutEvenWithCorrectCode() {
        final UserRepository userRepo = Mockito.mock(UserRepository.class);
        final OtpCodeRepository otpRepo = Mockito.mock(OtpCodeRepository.class);
        Mockito.when(userRepo.findByUsername("admin")).thenReturn(Optional.of(otpUser()));
        // Limite de intentos fallidos es 2 -- este codigo ya llego al limite.
        final OtpCode lockedCode = new OtpCode("uuid-code", "admin@test.com", OtpChannel.EMAIL, "123456",
                Instant.now().plusSeconds(60), false, Instant.now(), 2);
        Mockito.when(otpRepo.findLatestActive("admin@test.com")).thenReturn(Optional.of(lockedCode));

        assertThrows(IllegalArgumentException.class, () -> useCase(userRepo, otpRepo)
                .execute(new VerifyLoginOtpUseCase.Request("admin", "123456", null)));
        Mockito.verify(otpRepo, Mockito.never()).markConsumed(Mockito.anyString());
    }

    @Test
    void verify_accountNotUsingOtp_throwsGenericError() {
        final UserRepository userRepo = Mockito.mock(UserRepository.class);
        final OtpCodeRepository otpRepo = Mockito.mock(OtpCodeRepository.class);
        final User passwordUser = new User("1", "uuid-2", "regular", "Regular", "regular@test.com", null,
                java.util.List.of(), false, false, java.util.Set.of(UserRole.ORGANIZER), UserStatus.ACTIVE,
                "hash", Instant.now(), Instant.now());
        Mockito.when(userRepo.findByUsername("regular")).thenReturn(Optional.of(passwordUser));

        assertThrows(IllegalArgumentException.class, () -> useCase(userRepo, otpRepo)
                .execute(new VerifyLoginOtpUseCase.Request("regular", "123456", null)));
        Mockito.verifyNoInteractions(otpRepo);
    }

    @Test
    void verify_expiredCode_throws() {
        final UserRepository userRepo = Mockito.mock(UserRepository.class);
        final OtpCodeRepository otpRepo = Mockito.mock(OtpCodeRepository.class);
        Mockito.when(userRepo.findByUsername("admin")).thenReturn(Optional.of(otpUser()));
        final OtpCode expired = new OtpCode("admin@test.com", OtpChannel.EMAIL, "123456", Instant.now().minusSeconds(1));
        Mockito.when(otpRepo.findLatestActive("admin@test.com")).thenReturn(Optional.of(expired));

        assertThrows(IllegalArgumentException.class, () -> useCase(userRepo, otpRepo)
                .execute(new VerifyLoginOtpUseCase.Request("admin", "123456", null)));
    }
}
