package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.PlatformDeviceBlock;
import dev.rafex.insightbloom.users.domain.ports.PlatformDeviceBlockRepository;

import java.util.List;

public class ListPlatformDeviceBlocksUseCase {
    private final PlatformDeviceBlockRepository repository;

    public ListPlatformDeviceBlocksUseCase(final PlatformDeviceBlockRepository repository) {
        this.repository = repository;
    }

    public List<PlatformDeviceBlock> execute() {
        return repository.findAll();
    }
}
