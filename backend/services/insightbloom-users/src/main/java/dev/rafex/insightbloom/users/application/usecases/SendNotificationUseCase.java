package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.ether.json.JsonUtils;
import dev.rafex.insightbloom.users.domain.model.Notification;
import dev.rafex.insightbloom.users.domain.ports.NotificationRepository;
import dev.rafex.insightbloom.users.domain.services.NotificationStreamRegistry;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Punto único de emisión de notificaciones in-app: guarda la notificación y, si el usuario tiene
 * una pestaña conectada al stream SSE, la empuja en vivo. Pensado para que otros casos de uso
 * (ej. "tu zip de workspace ya está listo") solo necesiten llamar a execute(...) sin conocer nada
 * de SSE ni de persistencia.
 */
public class SendNotificationUseCase {
    private final NotificationRepository notificationRepository;
    private final NotificationStreamRegistry streamRegistry;

    public SendNotificationUseCase(final NotificationRepository notificationRepository,
                                    final NotificationStreamRegistry streamRegistry) {
        this.notificationRepository = notificationRepository;
        this.streamRegistry = streamRegistry;
    }

    public Notification execute(final String userUuid, final String type, final String title,
                                 final String body, final String linkUrl) {
        final Notification notification = new Notification(
                UUID.randomUUID().toString(), userUuid, type, title, body, linkUrl, Instant.now(), null);
        notificationRepository.save(notification);
        streamRegistry.push(userUuid, "notification", JsonUtils.toJson(view(notification)));
        return notification;
    }

    private static Map<String, Object> view(final Notification n) {
        final Map<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("uuid", n.getUuid());
        map.put("type", n.getType());
        map.put("title", n.getTitle());
        map.put("body", n.getBody());
        map.put("linkUrl", n.getLinkUrl());
        map.put("createdAt", n.getCreatedAt().toString());
        map.put("readAt", null);
        return map;
    }
}
