package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.ports.PlatformDeviceBlockRepository;

/** Un system_admin decide desde /dashboard/admin/device-access si desbloquea un dispositivo. */
public class UnblockPlatformDeviceUseCase {
    private final PlatformDeviceBlockRepository repository;

    public UnblockPlatformDeviceUseCase(final PlatformDeviceBlockRepository repository) {
        this.repository = repository;
    }

    public void execute(final String blockUuid, final String unblockedByUserUuid) {
        repository.unblock(blockUuid, unblockedByUserUuid);
    }
}
