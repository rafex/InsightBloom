package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.DeviceFingerprintFlag;
import dev.rafex.insightbloom.users.domain.ports.DeviceFingerprintFlagRepository;

import java.util.List;

public class ListDeviceFingerprintFlagsUseCase {
    private final DeviceFingerprintFlagRepository repository;

    public ListDeviceFingerprintFlagsUseCase(final DeviceFingerprintFlagRepository repository) {
        this.repository = repository;
    }

    public List<DeviceFingerprintFlag> execute() {
        return repository.findAll();
    }
}
