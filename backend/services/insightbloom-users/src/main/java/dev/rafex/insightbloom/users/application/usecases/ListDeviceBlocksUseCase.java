package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.DeviceBlock;
import dev.rafex.insightbloom.users.domain.ports.DeviceBlockRepository;

import java.util.List;

public class ListDeviceBlocksUseCase {
    private final DeviceBlockRepository deviceBlockRepository;

    public ListDeviceBlocksUseCase(final DeviceBlockRepository deviceBlockRepository) {
        this.deviceBlockRepository = deviceBlockRepository;
    }

    public List<DeviceBlock> execute(final String conferenceUuid) {
        return deviceBlockRepository.findByConference(conferenceUuid);
    }
}
