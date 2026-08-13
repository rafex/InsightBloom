package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.ether.http.core.HttpExchange.EventStream;
import dev.rafex.insightbloom.users.domain.model.Notification;
import dev.rafex.insightbloom.users.domain.ports.NotificationRepository;
import dev.rafex.insightbloom.users.domain.services.NotificationStreamRegistry;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SendNotificationUseCaseTest {
    private static final class FakeStream implements EventStream {
        final List<String[]> sent = new ArrayList<>();
        @Override public void send(final String event, final String data) { sent.add(new String[]{event, data}); }
        @Override public void comment(final String text) { }
        @Override public void onClose(final Runnable callback) { }
        @Override public void close() { }
    }

    @Test
    void savesAndPushesToConnectedStream() {
        final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        try {
            final NotificationRepository repository = mock(NotificationRepository.class);
            final NotificationStreamRegistry registry = new NotificationStreamRegistry(scheduler);
            final FakeStream stream = new FakeStream();
            registry.register("user-1", stream);

            final Notification result = new SendNotificationUseCase(repository, registry)
                    .execute("user-1", "workspace_zip_ready", "Tu zip está listo",
                            "Ya podés descargarlo", "/dashboard/events/conf-1");

            assertNotNull(result.getUuid());
            assertEquals("user-1", result.getUserUuid());
            verify(repository).save(result);
            assertEquals(1, stream.sent.size());
            assertEquals("notification", stream.sent.get(0)[0]);
            assertTrue(stream.sent.get(0)[1].contains("Tu zip está listo"));
        } finally {
            scheduler.shutdown();
        }
    }

    @Test
    void savesEvenWithoutConnectedStream() {
        final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        try {
            final NotificationRepository repository = mock(NotificationRepository.class);
            final NotificationStreamRegistry registry = new NotificationStreamRegistry(scheduler);

            final Notification result = new SendNotificationUseCase(repository, registry)
                    .execute("user-2", "workspace_zip_ready", "Listo", "body", null);

            verify(repository).save(result);
        } finally {
            scheduler.shutdown();
        }
    }
}
