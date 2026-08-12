package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.Notification;
import dev.rafex.insightbloom.users.domain.ports.NotificationRepository;

import java.util.List;

public class ListNotificationsUseCase {
    private final NotificationRepository notificationRepository;

    public ListNotificationsUseCase(final NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    public record Result(List<Notification> items, int unreadCount) {}

    public Result execute(final String userUuid, final int limit, final int offset) {
        final int boundedLimit = Math.max(1, Math.min(limit, 50));
        final int boundedOffset = Math.max(0, offset);
        final List<Notification> items = notificationRepository.findByUser(userUuid, boundedLimit, boundedOffset);
        final int unreadCount = notificationRepository.countUnread(userUuid);
        return new Result(items, unreadCount);
    }
}
