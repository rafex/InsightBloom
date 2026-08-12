package dev.rafex.insightbloom.users.domain.ports;

import dev.rafex.insightbloom.users.domain.model.Notification;

import java.util.List;
import java.util.Optional;

public interface NotificationRepository {
    Notification save(Notification notification);

    List<Notification> findByUser(String userUuid, int limit, int offset);

    int countUnread(String userUuid);

    Optional<Notification> findByUuid(String uuid);

    /** No-op si ya estaba leída o no le pertenece al usuario -- ver MarkNotificationReadUseCase. */
    boolean markRead(String uuid, String userUuid, java.time.Instant readAt);
}
