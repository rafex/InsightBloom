package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.ports.DeviceBlockRepository;

/** El moderador decide desde el dashboard ("Bloqueos") si desbloquea un dispositivo. */
public class UnblockDeviceUseCase {
    private final DeviceBlockRepository deviceBlockRepository;

    public UnblockDeviceUseCase(final DeviceBlockRepository deviceBlockRepository) {
        this.deviceBlockRepository = deviceBlockRepository;
    }

    public void execute(final String blockUuid, final String unblockedByUserUuid) {
        deviceBlockRepository.unblock(blockUuid, unblockedByUserUuid);
    }
}
