package dev.rafex.insightbloom.users.domain.ports;

import dev.rafex.insightbloom.users.domain.model.ToolDeviceSession;
import dev.rafex.insightbloom.users.domain.model.ToolKind;

import java.util.List;
import java.util.Optional;

public interface ToolDeviceSessionRepository {
    void save(ToolDeviceSession session);

    void touch(String uuid);

    Optional<ToolDeviceSession> findActive(String conferenceUuid, String userUuid, ToolKind tool, String deviceFingerprint);

    List<ToolDeviceSession> findActiveByUserAndTool(String conferenceUuid, String userUuid, ToolKind tool);

    List<ToolDeviceSession> findActiveByDevice(String conferenceUuid, String deviceFingerprint);

    void revoke(String uuid);

    void revokeAllForDevice(String conferenceUuid, String deviceFingerprint);
}
