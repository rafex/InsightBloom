package dev.rafex.insightbloom.query.domain.ports;

import dev.rafex.insightbloom.query.domain.model.MessageType;

import java.util.Map;
import java.util.function.Consumer;

/**
 * Cross-pod fan-out for cloud word updates, so an SSE connection held by one replica gets
 * notified of writes that landed on a different replica (each query pod has its own SQLite file).
 * Payloads are plain maps (same convention as the rest of the codebase's internal JSON bodies)
 * rather than domain objects, since the message only needs to reach the browser as-is.
 */
public interface CloudEventBus {

    void publish(String conferenceUuid, MessageType type, Map<String, Object> payload);

    /** Subscribes to updates for a conference+type; returns a handle to unsubscribe. */
    AutoCloseable subscribe(String conferenceUuid, MessageType type, Consumer<Map<String, Object>> onUpdate);
}
