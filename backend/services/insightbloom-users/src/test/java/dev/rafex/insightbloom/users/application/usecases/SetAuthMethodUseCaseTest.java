package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.*;
import dev.rafex.insightbloom.users.domain.ports.UserRepository;
import dev.rafex.insightbloom.users.domain.services.PasswordService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SetAuthMethodUseCaseTest {

    private final PasswordService passwordService = new PasswordService();

    private User userWithPassword(final String password) {
        return new User("1", "uuid-1", "admin", "Admin", "admin@test.com", null, java.util.List.of(),
                false, false, java.util.Set.of(UserRole.ORGANIZER), UserStatus.ACTIVE,
                passwordService.hash(password), Instant.now(), Instant.now());
    }

    @Test
    void execute_correctPassword_activatesOtp() {
        final UserRepository repo = Mockito.mock(UserRepository.class);
        final User user = userWithPassword("pass");
        Mockito.when(repo.findByUuid("uuid-1")).thenReturn(Optional.of(user));

        final boolean changed = new SetAuthMethodUseCase(repo, passwordService)
                .execute("uuid-1", new SetAuthMethodUseCase.Request("pass", AuthMethod.OTP_EMAIL));

        assertTrue(changed);
        assertEquals(AuthMethod.OTP_EMAIL, user.getAuthMethod());
        Mockito.verify(repo).save(user);
    }

    @Test
    void execute_wrongPassword_doesNotChange() {
        final UserRepository repo = Mockito.mock(UserRepository.class);
        final User user = userWithPassword("pass");
        Mockito.when(repo.findByUuid("uuid-1")).thenReturn(Optional.of(user));

        final boolean changed = new SetAuthMethodUseCase(repo, passwordService)
                .execute("uuid-1", new SetAuthMethodUseCase.Request("wrong", AuthMethod.OTP_EMAIL));

        assertFalse(changed);
        assertEquals(AuthMethod.PASSWORD, user.getAuthMethod());
        Mockito.verify(repo, Mockito.never()).save(Mockito.any());
    }

    @Test
    void execute_backToPassword_requiresCurrentPasswordToo() {
        final UserRepository repo = Mockito.mock(UserRepository.class);
        final User user = userWithPassword("pass");
        user.setAuthMethod(AuthMethod.OTP_EMAIL);
        Mockito.when(repo.findByUuid("uuid-1")).thenReturn(Optional.of(user));

        final boolean changed = new SetAuthMethodUseCase(repo, passwordService)
                .execute("uuid-1", new SetAuthMethodUseCase.Request("pass", AuthMethod.PASSWORD));

        assertTrue(changed);
        assertEquals(AuthMethod.PASSWORD, user.getAuthMethod());
    }

    @Test
    void execute_nullNewMethod_throws() {
        final UserRepository repo = Mockito.mock(UserRepository.class);
        assertThrows(IllegalArgumentException.class, () -> new SetAuthMethodUseCase(repo, passwordService)
                .execute("uuid-1", new SetAuthMethodUseCase.Request("pass", null)));
    }

    @Test
    void execute_unknownUser_returnsFalse() {
        final UserRepository repo = Mockito.mock(UserRepository.class);
        Mockito.when(repo.findByUuid("missing")).thenReturn(Optional.empty());

        final boolean changed = new SetAuthMethodUseCase(repo, passwordService)
                .execute("missing", new SetAuthMethodUseCase.Request("pass", AuthMethod.OTP_EMAIL));

        assertFalse(changed);
    }
}
