package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.ports.NotificationRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MarkNotificationReadUseCaseTest {
    @Test
    void marksWhenRepositoryConfirms() {
        final NotificationRepository repository = mock(NotificationRepository.class);
        when(repository.markRead(eq("n1"), eq("user-1"), any(Instant.class))).thenReturn(true);

        assertTrue(new MarkNotificationReadUseCase(repository).execute("n1", "user-1"));
    }

    @Test
    void returnsFalseWhenAlreadyReadOrNotOwned() {
        final NotificationRepository repository = mock(NotificationRepository.class);
        when(repository.markRead(eq("n1"), eq("other-user"), any(Instant.class))).thenReturn(false);

        assertFalse(new MarkNotificationReadUseCase(repository).execute("n1", "other-user"));
    }
}
