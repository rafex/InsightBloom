package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.ports.NotificationRepository;

import java.time.Instant;

public class MarkNotificationReadUseCase {
    private final NotificationRepository notificationRepository;

    public MarkNotificationReadUseCase(final NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    /** @return true si marcó algo (idempotente: false si ya estaba leída, no existía o no era del usuario). */
    public boolean execute(final String uuid, final String userUuid) {
        return notificationRepository.markRead(uuid, userUuid, Instant.now());
    }
}
