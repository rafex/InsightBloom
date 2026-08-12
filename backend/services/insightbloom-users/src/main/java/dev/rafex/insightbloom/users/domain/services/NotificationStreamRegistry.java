package dev.rafex.insightbloom.users.domain.services;

import dev.rafex.ether.http.core.HttpExchange.EventStream;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Conexiones SSE abiertas por usuario (una pestaña puede abrir más de una). Vive en memoria de
 * un solo proceso -- si el usuario reconecta a otro pod de insightbloom-users no recibe el push
 * en vivo de ese pod hasta su próximo poll/reconexión, mismo trade-off que ya acepta el resto de
 * los streams SSE de este servicio (sandbox status, whiteboard, video-session). No es un bus
 * multi-pod: el registro solo entrega notificaciones a streams conectados al mismo proceso que
 * las inserta.
 */
public final class NotificationStreamRegistry {
    private final ConcurrentHashMap<String, Set<EventStream>> streamsByUser = new ConcurrentHashMap<>();

    public void register(final String userUuid, final EventStream stream) {
        streamsByUser.computeIfAbsent(userUuid, ignored -> new CopyOnWriteArraySet<>()).add(stream);
    }

    public void unregister(final String userUuid, final EventStream stream) {
        final Set<EventStream> streams = streamsByUser.get(userUuid);
        if (streams == null) return;
        streams.remove(stream);
        if (streams.isEmpty()) streamsByUser.remove(userUuid, streams);
    }

    /** Empuja el evento a cada stream conectado de ese usuario. Streams que fallen al escribir
     * (conexión ya caída del lado del cliente) se descartan silenciosamente. */
    public void push(final String userUuid, final String eventName, final String jsonPayload) {
        final Set<EventStream> streams = streamsByUser.get(userUuid);
        if (streams == null || streams.isEmpty()) return;
        for (final EventStream stream : streams) {
            try {
                stream.send(eventName, jsonPayload);
            } catch (final Exception ignored) {
                // Se limpia solo cuando el propio stream dispare onClose (ver NotificationHandler).
            }
        }
    }
}
