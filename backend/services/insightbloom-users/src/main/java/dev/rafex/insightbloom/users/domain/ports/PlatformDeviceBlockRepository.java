package dev.rafex.insightbloom.users.domain.ports;

import dev.rafex.insightbloom.users.domain.model.PlatformDeviceBlock;

import java.util.List;
import java.util.Optional;

public interface PlatformDeviceBlockRepository {
    void save(PlatformDeviceBlock block);

    Optional<PlatformDeviceBlock> findActive(String deviceFingerprint);

    List<PlatformDeviceBlock> findAll();

    void unblock(String uuid, String unblockedByUserUuid);
}
