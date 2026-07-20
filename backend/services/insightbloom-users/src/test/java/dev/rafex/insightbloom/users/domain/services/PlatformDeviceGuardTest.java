package dev.rafex.insightbloom.users.domain.services;

import dev.rafex.insightbloom.users.domain.model.PlatformDeviceBlock;
import dev.rafex.insightbloom.users.domain.model.PlatformDeviceBlockReason;
import dev.rafex.insightbloom.users.domain.model.PlatformSettings;
import dev.rafex.insightbloom.users.domain.model.Token;
import dev.rafex.insightbloom.users.domain.model.TokenKind;
import dev.rafex.insightbloom.users.domain.ports.PlatformDeviceBlockRepository;
import dev.rafex.insightbloom.users.domain.ports.TokenRepository;
import dev.rafex.insightbloom.users.domain.ports.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class PlatformDeviceGuardTest {

    private static PlatformSettings settings(final Integer maxAccountsPerDevice, final Integer maxSessionsPerUser,
                                               final Integer maxRegistrationsPerDay) {
        final PlatformSettings s = PlatformSettings.defaults();
        s.setMaxAccountsPerDevice(maxAccountsPerDevice);
        s.setMaxSessionsPerUser(maxSessionsPerUser);
        s.setMaxRegistrationsPerDevicePerDay(maxRegistrationsPerDay);
        return s;
    }

    @Test
    void exceedingSessionLimit_revokesOldestSessionAndAllows() {
        final TokenRepository tokenRepo = Mockito.mock(TokenRepository.class);
        final UserRepository userRepo = Mockito.mock(UserRepository.class);
        final PlatformDeviceBlockRepository blockRepo = Mockito.mock(PlatformDeviceBlockRepository.class);
        final var guard = new PlatformDeviceGuard(tokenRepo, userRepo, blockRepo);

        final Token oldest = new Token("user-1", null, TokenKind.USER, "raw-1", Instant.now().plusSeconds(3600), "fp-a");
        final Token newer = new Token("user-1", null, TokenKind.USER, "raw-2", Instant.now().plusSeconds(3600), "fp-b");

        Mockito.when(blockRepo.findActive("fp-new")).thenReturn(Optional.empty());
        // Ya al limite (2 sesiones) cuando llega una tercera -- se espera que se revoque la mas
        // vieja (primero de la lista, ver contrato de ordenamiento ASC de findActiveByUser).
        Mockito.when(tokenRepo.findActiveByUser("user-1")).thenReturn(List.of(oldest, newer));
        Mockito.when(tokenRepo.findActiveByFingerprint("fp-new")).thenReturn(List.of(
                new Token("user-1", null, TokenKind.USER, "raw-3", Instant.now().plusSeconds(3600), "fp-new")));

        final var result = guard.checkAndRegisterLogin("fp-new", "user-1", TokenKind.USER, settings(5, 2, 3));

        assertInstanceOf(PlatformDeviceGuard.Result.Allowed.class, result);
        Mockito.verify(tokenRepo).revokeByUuid(oldest.getUuid());
        Mockito.verify(tokenRepo, Mockito.never()).revokeByUuid(newer.getUuid());
    }

    @Test
    void exceedingAccountsPerDevice_blocksAndRevokesAllTokensForFingerprint() {
        final TokenRepository tokenRepo = Mockito.mock(TokenRepository.class);
        final UserRepository userRepo = Mockito.mock(UserRepository.class);
        final PlatformDeviceBlockRepository blockRepo = Mockito.mock(PlatformDeviceBlockRepository.class);
        final var guard = new PlatformDeviceGuard(tokenRepo, userRepo, blockRepo);

        Mockito.when(blockRepo.findActive("shared-fp")).thenReturn(Optional.empty());
        Mockito.when(tokenRepo.findActiveByUser("user-3")).thenReturn(List.of());
        // 3 cuentas distintas ya comparten este fingerprint -- supera maxAccountsPerDevice=2.
        Mockito.when(tokenRepo.findActiveByFingerprint("shared-fp")).thenReturn(List.of(
                new Token("user-1", null, TokenKind.USER, "raw-1", Instant.now().plusSeconds(3600), "shared-fp"),
                new Token("user-2", null, TokenKind.USER, "raw-2", Instant.now().plusSeconds(3600), "shared-fp"),
                new Token("user-3", null, TokenKind.USER, "raw-3", Instant.now().plusSeconds(3600), "shared-fp")
        ));

        final var result = guard.checkAndRegisterLogin("shared-fp", "user-3", TokenKind.USER, settings(2, 10, 3));

        assertInstanceOf(PlatformDeviceGuard.Result.Blocked.class, result);
        assertEquals(PlatformDeviceBlockReason.MULTI_ACCOUNT, ((PlatformDeviceGuard.Result.Blocked) result).reason());
        Mockito.verify(blockRepo).save(Mockito.any(PlatformDeviceBlock.class));
        Mockito.verify(tokenRepo).revokeAllForFingerprint("shared-fp");
    }

    @Test
    void registrationSpam_blocksAfterThreshold() {
        final TokenRepository tokenRepo = Mockito.mock(TokenRepository.class);
        final UserRepository userRepo = Mockito.mock(UserRepository.class);
        final PlatformDeviceBlockRepository blockRepo = Mockito.mock(PlatformDeviceBlockRepository.class);
        final var guard = new PlatformDeviceGuard(tokenRepo, userRepo, blockRepo);

        Mockito.when(blockRepo.findActive("spam-fp")).thenReturn(Optional.empty());
        Mockito.when(userRepo.countByRegistrationFingerprintSince(Mockito.eq("spam-fp"), Mockito.any())).thenReturn(3L);

        final var result = guard.checkRegistration("spam-fp", settings(5, 3, 3));

        assertInstanceOf(PlatformDeviceGuard.Result.Blocked.class, result);
        assertEquals(PlatformDeviceBlockReason.REGISTRATION_SPAM,
                ((PlatformDeviceGuard.Result.Blocked) result).reason());
        Mockito.verify(blockRepo).save(Mockito.any(PlatformDeviceBlock.class));
    }

    @Test
    void registrationUnderThreshold_isAllowed() {
        final TokenRepository tokenRepo = Mockito.mock(TokenRepository.class);
        final UserRepository userRepo = Mockito.mock(UserRepository.class);
        final PlatformDeviceBlockRepository blockRepo = Mockito.mock(PlatformDeviceBlockRepository.class);
        final var guard = new PlatformDeviceGuard(tokenRepo, userRepo, blockRepo);

        Mockito.when(blockRepo.findActive("fresh-fp")).thenReturn(Optional.empty());
        Mockito.when(userRepo.countByRegistrationFingerprintSince(Mockito.eq("fresh-fp"), Mockito.any())).thenReturn(1L);

        final var result = guard.checkRegistration("fresh-fp", settings(5, 3, 3));

        assertInstanceOf(PlatformDeviceGuard.Result.Allowed.class, result);
        Mockito.verify(blockRepo, Mockito.never()).save(Mockito.any());
    }

    @Test
    void alreadyBlockedDevice_isRejectedWithoutCountingAgain() {
        final TokenRepository tokenRepo = Mockito.mock(TokenRepository.class);
        final UserRepository userRepo = Mockito.mock(UserRepository.class);
        final PlatformDeviceBlockRepository blockRepo = Mockito.mock(PlatformDeviceBlockRepository.class);
        final var guard = new PlatformDeviceGuard(tokenRepo, userRepo, blockRepo);

        Mockito.when(blockRepo.findActive("blocked-fp"))
                .thenReturn(Optional.of(new PlatformDeviceBlock("blocked-fp", PlatformDeviceBlockReason.MULTI_ACCOUNT, 6)));

        final var loginResult = guard.checkAndRegisterLogin("blocked-fp", "user-9", TokenKind.USER, settings(5, 3, 3));
        assertInstanceOf(PlatformDeviceGuard.Result.Blocked.class, loginResult);
        Mockito.verify(tokenRepo, Mockito.never()).findActiveByUser(Mockito.any());
        Mockito.verify(tokenRepo, Mockito.never()).findActiveByFingerprint(Mockito.any());

        final var registrationResult = guard.checkRegistration("blocked-fp", settings(5, 3, 3));
        assertInstanceOf(PlatformDeviceGuard.Result.Blocked.class, registrationResult);
        Mockito.verify(userRepo, Mockito.never()).countByRegistrationFingerprintSince(Mockito.any(), Mockito.any());
    }

    @Test
    void nullFingerprint_isAlwaysAllowed() {
        final TokenRepository tokenRepo = Mockito.mock(TokenRepository.class);
        final UserRepository userRepo = Mockito.mock(UserRepository.class);
        final PlatformDeviceBlockRepository blockRepo = Mockito.mock(PlatformDeviceBlockRepository.class);
        final var guard = new PlatformDeviceGuard(tokenRepo, userRepo, blockRepo);

        assertInstanceOf(PlatformDeviceGuard.Result.Allowed.class,
                guard.checkAndRegisterLogin(null, "user-1", TokenKind.USER, settings(5, 3, 3)));
        assertInstanceOf(PlatformDeviceGuard.Result.Allowed.class,
                guard.checkRegistration(null, settings(5, 3, 3)));
        Mockito.verifyNoInteractions(blockRepo);
    }
}
