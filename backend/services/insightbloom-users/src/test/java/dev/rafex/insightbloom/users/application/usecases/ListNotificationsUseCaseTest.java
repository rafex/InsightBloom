package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.Notification;
import dev.rafex.insightbloom.users.domain.ports.NotificationRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ListNotificationsUseCaseTest {
    @Test
    void returnsItemsAndUnreadCount() {
        final NotificationRepository repository = mock(NotificationRepository.class);
        final Notification n = new Notification("n1", "user-1", "workspace_zip_ready", "Listo",
                "Tu zip está listo", "/dashboard", Instant.now(), null);
        when(repository.findByUser("user-1", 20, 0)).thenReturn(List.of(n));
        when(repository.countUnread("user-1")).thenReturn(3);

        final var result = new ListNotificationsUseCase(repository).execute("user-1", 20, 0);

        assertEquals(List.of(n), result.items());
        assertEquals(3, result.unreadCount());
    }

    @Test
    void boundsLimitAndOffset() {
        final NotificationRepository repository = mock(NotificationRepository.class);
        when(repository.findByUser("user-1", 50, 0)).thenReturn(List.of());

        new ListNotificationsUseCase(repository).execute("user-1", 500, -10);

        verify(repository).findByUser("user-1", 50, 0);
    }
}
