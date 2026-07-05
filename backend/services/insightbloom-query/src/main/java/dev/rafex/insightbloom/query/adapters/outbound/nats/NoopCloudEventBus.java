package dev.rafex.insightbloom.query.adapters.outbound.nats;

import dev.rafex.insightbloom.query.domain.model.MessageType;
import dev.rafex.insightbloom.query.domain.ports.CloudEventBus;

import java.util.Map;
import java.util.function.Consumer;

/**
 * Degraded-mode fallback when NATS is unreachable at startup (mirrors live.js's
 * "sigue en modo local" behavior): the SSE endpoints stay up and the initial snapshot still
 * works, but live push stops working until the pod is restarted with NATS reachable again.
 */
public class NoopCloudEventBus implements CloudEventBus {

    @Override
    public void publish(final String conferenceUuid, final MessageType type, final Map<String, Object> payload) {
        // no-op
    }

    @Override
    public AutoCloseable subscribe(final String conferenceUuid, final MessageType type,
                                    final Consumer<Map<String, Object>> onUpdate) {
        return () -> { };
    }
}
