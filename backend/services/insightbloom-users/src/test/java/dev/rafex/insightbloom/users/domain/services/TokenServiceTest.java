package dev.rafex.insightbloom.users.domain.services;

import dev.rafex.insightbloom.users.domain.model.Token;
import dev.rafex.insightbloom.users.domain.model.TokenKind;
import dev.rafex.insightbloom.users.domain.ports.TokenRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class TokenServiceTest {

    @Test
    void userTokensExpireAfterOneHour() {
        final TokenRepository repository = mock(TokenRepository.class);
        final Instant before = Instant.now();

        new TokenService(repository).issueUserToken("user-1", TokenKind.USER);

        final ArgumentCaptor<Token> captor = ArgumentCaptor.forClass(Token.class);
        verify(repository).save(captor.capture());
        assertOneHourFrom(before, captor.getValue().getExpiresAt());
    }

    @Test
    void guestTokensExpireAfterOneHour() {
        final TokenRepository repository = mock(TokenRepository.class);
        final Instant before = Instant.now();

        new TokenService(repository).issueGuestToken("guest-1");

        final ArgumentCaptor<Token> captor = ArgumentCaptor.forClass(Token.class);
        verify(repository).save(captor.capture());
        assertOneHourFrom(before, captor.getValue().getExpiresAt());
    }

    private static void assertOneHourFrom(final Instant before, final Instant expiresAt) {
        final Duration lifetime = Duration.between(before, expiresAt);
        assertTrue(lifetime.compareTo(Duration.ofMinutes(59)) >= 0,
                "token lifetime should be at least 59 minutes");
        assertTrue(lifetime.compareTo(Duration.ofMinutes(61)) <= 0,
                "token lifetime should be at most 61 minutes");
    }
}
