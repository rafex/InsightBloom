package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.*;
import dev.rafex.insightbloom.users.domain.ports.PlatformSettingsRepository;
import dev.rafex.insightbloom.users.domain.ports.TokenRepository;
import dev.rafex.insightbloom.users.domain.ports.UserRepository;
import dev.rafex.insightbloom.users.domain.services.PasswordService;
import dev.rafex.insightbloom.users.domain.services.PlatformDeviceGuard;
import dev.rafex.insightbloom.users.domain.services.TokenService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class LoginUseCaseTest {

    private final PasswordService passwordService = new PasswordService();

    private LoginUseCase useCase(final UserRepository repo) {
        final TokenRepository tokenRepo = Mockito.mock(TokenRepository.class);
        Mockito.when(tokenRepo.findActiveByUser(Mockito.anyString())).thenReturn(List.of());
        Mockito.when(tokenRepo.findActiveByFingerprint(Mockito.anyString())).thenReturn(List.of());
        final PlatformSettingsRepository settingsRepo = Mockito.mock(PlatformSettingsRepository.class);
        Mockito.when(settingsRepo.get()).thenReturn(PlatformSettings.defaults());
        final var platformDeviceBlockRepo =
                Mockito.mock(dev.rafex.insightbloom.users.domain.ports.PlatformDeviceBlockRepository.class);
        Mockito.when(platformDeviceBlockRepo.findActive(Mockito.anyString())).thenReturn(Optional.empty());
        final var platformDeviceGuard = new PlatformDeviceGuard(tokenRepo, repo, platformDeviceBlockRepo);
        return new LoginUseCase(repo, new TokenService(tokenRepo), passwordService, platformDeviceGuard, settingsRepo);
    }

    @Test
    void login_existingUser_returnsToken() {
        final UserRepository repo = Mockito.mock(UserRepository.class);
        // Store a legacy SHA-256 hash — verifies backward-compat path
        final User user = new User("1", "uuid-1", "admin", "Admin", "admin@test.com", null, java.util.List.of(),
                false, false, java.util.Set.of(UserRole.ORGANIZER), UserStatus.ACTIVE,
                PasswordService.sha256("pass"),
                java.time.Instant.now(), java.time.Instant.now());
        Mockito.when(repo.findByUsername("admin")).thenReturn(Optional.of(user));
        Mockito.when(repo.findByEmail("admin")).thenReturn(Optional.empty());

        final var result = useCase(repo).execute(new LoginUseCase.LoginRequest("admin", "pass", null));
        assertTrue(result.isPresent());
        assertNotNull(result.get().token());
        assertEquals("uuid-1", result.get().userUuid());
        assertEquals("organizer", result.get().role());
    }

    @Test
    void login_pbkdf2Hash_returnsToken() {
        final UserRepository repo = Mockito.mock(UserRepository.class);
        final User user = new User("1", "uuid-2", "admin", "Admin", "admin@test.com", null, java.util.List.of(),
                false, false, java.util.Set.of(UserRole.ORGANIZER), UserStatus.ACTIVE,
                passwordService.hash("pass"),
                java.time.Instant.now(), java.time.Instant.now());
        Mockito.when(repo.findByUsername("admin")).thenReturn(Optional.of(user));
        Mockito.when(repo.findByEmail("admin")).thenReturn(Optional.empty());

        final var result = useCase(repo).execute(new LoginUseCase.LoginRequest("admin", "pass", null));
        assertTrue(result.isPresent());
    }

    @Test
    void login_unknownUser_returnsEmpty() {
        final UserRepository repo = Mockito.mock(UserRepository.class);
        Mockito.when(repo.findByUsername("nobody")).thenReturn(Optional.empty());
        Mockito.when(repo.findByEmail("nobody")).thenReturn(Optional.empty());

        assertTrue(useCase(repo).execute(new LoginUseCase.LoginRequest("nobody", "pass", null)).isEmpty());
    }

    @Test
    void login_wrongPassword_returnsEmpty() {
        final UserRepository repo = Mockito.mock(UserRepository.class);
        final User user = new User("1", "uuid-1", "admin", "Admin", "admin@test.com", null, java.util.List.of(),
                false, false, java.util.Set.of(UserRole.ORGANIZER), UserStatus.ACTIVE,
                PasswordService.sha256("correct"),
                java.time.Instant.now(), java.time.Instant.now());
        Mockito.when(repo.findByUsername("admin")).thenReturn(Optional.of(user));
        Mockito.when(repo.findByEmail("admin")).thenReturn(Optional.empty());

        assertTrue(useCase(repo).execute(new LoginUseCase.LoginRequest("admin", "wrong", null)).isEmpty());
    }

    @Test
    void login_blankPassword_returnsEmpty() {
        final UserRepository repo = Mockito.mock(UserRepository.class);
        assertTrue(useCase(repo).execute(new LoginUseCase.LoginRequest("admin", "", null)).isEmpty());
    }

    @Test
    void login_otpEmailAccount_throwsOtpLoginRequired() {
        final UserRepository repo = Mockito.mock(UserRepository.class);
        final User user = new User("1", "uuid-1", "admin", "Admin", "admin@test.com", null, java.util.List.of(),
                false, false, java.util.Set.of(UserRole.ORGANIZER), UserStatus.ACTIVE,
                passwordService.hash("pass"),
                java.time.Instant.now(), java.time.Instant.now());
        user.setAuthMethod(AuthMethod.OTP_EMAIL);
        Mockito.when(repo.findByUsername("admin")).thenReturn(Optional.of(user));
        Mockito.when(repo.findByEmail("admin")).thenReturn(Optional.empty());

        assertThrows(dev.rafex.insightbloom.users.domain.services.OtpLoginRequiredException.class,
                () -> useCase(repo).execute(new LoginUseCase.LoginRequest("admin", "pass", null)));
    }
}
