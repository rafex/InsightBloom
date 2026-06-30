package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.*;
import dev.rafex.insightbloom.users.domain.ports.TokenRepository;
import dev.rafex.insightbloom.users.domain.ports.UserRepository;
import dev.rafex.insightbloom.users.domain.services.PasswordService;
import dev.rafex.insightbloom.users.domain.services.TokenService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class LoginUseCaseTest {

    private final PasswordService passwordService = new PasswordService();

    private LoginUseCase useCase(final UserRepository repo) {
        return new LoginUseCase(repo, new TokenService(Mockito.mock(TokenRepository.class)), passwordService);
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

        final var result = useCase(repo).execute(new LoginUseCase.LoginRequest("admin", "pass"));
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

        final var result = useCase(repo).execute(new LoginUseCase.LoginRequest("admin", "pass"));
        assertTrue(result.isPresent());
    }

    @Test
    void login_unknownUser_returnsEmpty() {
        final UserRepository repo = Mockito.mock(UserRepository.class);
        Mockito.when(repo.findByUsername("nobody")).thenReturn(Optional.empty());
        Mockito.when(repo.findByEmail("nobody")).thenReturn(Optional.empty());

        assertTrue(useCase(repo).execute(new LoginUseCase.LoginRequest("nobody", "pass")).isEmpty());
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

        assertTrue(useCase(repo).execute(new LoginUseCase.LoginRequest("admin", "wrong")).isEmpty());
    }

    @Test
    void login_blankPassword_returnsEmpty() {
        final UserRepository repo = Mockito.mock(UserRepository.class);
        assertTrue(useCase(repo).execute(new LoginUseCase.LoginRequest("admin", "")).isEmpty());
    }
}
