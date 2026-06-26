package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.*;
import dev.rafex.insightbloom.users.domain.ports.TokenRepository;
import dev.rafex.insightbloom.users.domain.ports.UserRepository;
import dev.rafex.insightbloom.users.domain.services.TokenService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class LoginUseCaseTest {
    @Test
    void login_existingUser_returnsToken() {
        UserRepository repo = Mockito.mock(UserRepository.class);
        TokenRepository tokenRepo = Mockito.mock(TokenRepository.class);
        TokenService tokenService = new TokenService(tokenRepo);

        User user = new User("1", "uuid-1", "admin", "Admin", "admin@test.com", null, java.util.List.of(),
                false, false, UserRole.ORGANIZER, UserStatus.ACTIVE, LoginUseCase.sha256("pass"),
                java.time.Instant.now(), java.time.Instant.now());
        Mockito.when(repo.findByUsername("admin")).thenReturn(Optional.of(user));

        LoginUseCase uc = new LoginUseCase(repo, tokenService);
        var result = uc.execute(new LoginUseCase.LoginRequest("admin", "pass"));
        assertTrue(result.isPresent());
        assertNotNull(result.get().token());
        assertEquals("uuid-1", result.get().userUuid());
        assertEquals("organizer", result.get().role());
    }

    @Test
    void login_unknownUser_returnsEmpty() {
        UserRepository repo = Mockito.mock(UserRepository.class);
        TokenRepository tokenRepo = Mockito.mock(TokenRepository.class);
        TokenService tokenService = new TokenService(tokenRepo);
        Mockito.when(repo.findByUsername("nobody")).thenReturn(Optional.empty());

        LoginUseCase uc = new LoginUseCase(repo, tokenService);
        assertTrue(uc.execute(new LoginUseCase.LoginRequest("nobody", "pass")).isEmpty());
    }

    @Test
    void login_wrongPassword_returnsEmpty() {
        UserRepository repo = Mockito.mock(UserRepository.class);
        TokenRepository tokenRepo = Mockito.mock(TokenRepository.class);
        TokenService tokenService = new TokenService(tokenRepo);

        User user = new User("1", "uuid-1", "admin", "Admin", "admin@test.com", null, java.util.List.of(),
                false, false, UserRole.ORGANIZER, UserStatus.ACTIVE, LoginUseCase.sha256("correct"),
                java.time.Instant.now(), java.time.Instant.now());
        Mockito.when(repo.findByUsername("admin")).thenReturn(Optional.of(user));

        LoginUseCase uc = new LoginUseCase(repo, tokenService);
        assertTrue(uc.execute(new LoginUseCase.LoginRequest("admin", "wrong")).isEmpty());
    }

    @Test
    void login_blankPassword_returnsEmpty() {
        UserRepository repo = Mockito.mock(UserRepository.class);
        TokenRepository tokenRepo = Mockito.mock(TokenRepository.class);
        TokenService tokenService = new TokenService(tokenRepo);

        LoginUseCase uc = new LoginUseCase(repo, tokenService);
        assertTrue(uc.execute(new LoginUseCase.LoginRequest("admin", "")).isEmpty());
    }
}
