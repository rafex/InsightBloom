package dev.rafex.insightbloom.users.domain.services;

import dev.rafex.ether.http.core.HttpExchange.EventStream;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Conexiones SSE abiertas por usuario (una pestaña puede abrir más de una). Vive en memoria de
 * un solo proceso -- si el usuario reconecta a otro pod de insightbloom-users no recibe el push
 * en vivo de ese pod hasta su próximo poll/reconexión, mismo trade-off que ya acepta el resto de
 * los streams SSE de este servicio (sandbox status, whiteboard, video-session). No es un bus
 * multi-pod: el registro solo entrega notificaciones a streams conectados al mismo proceso que
 * las inserta.
 *
 * HEARTBEAT: envía un comentario SSE cada 30 segundos a todos los streams activos para mantener
 * la conexión viva (previene 504 Gateway Time-out de nginx cuando hay silencio >60s sin eventos).
 * El cliente ignora estos comentarios (líneas que empiezan con ":").
 */
public final class NotificationStreamRegistry {
    private final ConcurrentHashMap<String, Set<EventStream>> streamsByUser = new ConcurrentHashMap<>();
    private final ScheduledExecutorService heartbeatScheduler;

    public NotificationStreamRegistry(final ScheduledExecutorService heartbeatScheduler) {
        this.heartbeatScheduler = heartbeatScheduler;
        // Enviar heartbeat (comentario SSE) cada 30 segundos a todos los streams para mantener viva
        // la conexión (previene nginx 504 Gateway Time-out cuando hay >60s sin eventos)
        heartbeatScheduler.scheduleAtFixedRate(this::sendHeartbeatToAll, 30, 30, TimeUnit.SECONDS);
    }

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

    private void sendHeartbeatToAll() {
        // Enviar comentario SSE (:heartbeat) a todos los streams conectados para mantener viva
        // la conexión (previene nginx 504 cuando hay >60s sin eventos normales).
        // Los clientes ignoran líneas que empiezan con ":", así que no afecta la UI.
        for (final Set<EventStream> streams : streamsByUser.values()) {
            for (final EventStream stream : streams) {
                try {
                    stream.send(":heartbeat", "");
                } catch (final Exception ignored) {
                    // Stream ya cerrado; se limpia en onClose()
                }
            }
        }
    }
}
