package dev.rafex.insightbloom.users.domain.ports;

import dev.rafex.insightbloom.users.domain.model.DeviceBlock;

import java.util.List;
import java.util.Optional;

public interface DeviceBlockRepository {
    void save(DeviceBlock block);

    Optional<DeviceBlock> findActive(String conferenceUuid, String deviceFingerprint);

    List<DeviceBlock> findByConference(String conferenceUuid);

    void unblock(String uuid, String unblockedByUserUuid);
}
