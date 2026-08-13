package dev.rafex.insightbloom.users.domain.services;

import dev.rafex.ether.http.core.HttpExchange.EventStream;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificationStreamRegistryTest {
    private static final class FakeStream implements EventStream {
        final List<String[]> sent = new ArrayList<>();
        @Override public void send(final String event, final String data) { sent.add(new String[]{event, data}); }
        @Override public void comment(final String text) { }
        @Override public void onClose(final Runnable callback) { }
        @Override public void close() { }
    }

    @Test
    void pushDeliversToAllStreamsOfTheSameUser() {
        final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        try {
            final NotificationStreamRegistry registry = new NotificationStreamRegistry(scheduler);
            final FakeStream a = new FakeStream();
            final FakeStream b = new FakeStream();
            registry.register("user-1", a);
            registry.register("user-1", b);

            registry.push("user-1", "notification", "{\"title\":\"hola\"}");

            assertEquals(1, a.sent.size());
            assertEquals("notification", a.sent.get(0)[0]);
            assertEquals(1, b.sent.size());
        } finally {
            scheduler.shutdown();
        }
    }

    @Test
    void pushDoesNotDeliverToOtherUsers() {
        final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        try {
            final NotificationStreamRegistry registry = new NotificationStreamRegistry(scheduler);
            final FakeStream a = new FakeStream();
            registry.register("user-1", a);

            registry.push("user-2", "notification", "{}");

            assertTrue(a.sent.isEmpty());
        } finally {
            scheduler.shutdown();
        }
    }

    @Test
    void unregisterStopsFurtherDelivery() {
        final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        try {
            final NotificationStreamRegistry registry = new NotificationStreamRegistry(scheduler);
            final FakeStream a = new FakeStream();
            registry.register("user-1", a);
            registry.unregister("user-1", a);

            registry.push("user-1", "notification", "{}");

            assertTrue(a.sent.isEmpty());
        } finally {
            scheduler.shutdown();
        }
    }

    @Test
    void pushWithNoConnectedStreamsIsNoop() {
        final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        try {
            final NotificationStreamRegistry registry = new NotificationStreamRegistry(scheduler);
            registry.push("nobody-connected", "notification", "{}");
        } finally {
            scheduler.shutdown();
        }
    }
}
