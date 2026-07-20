package dev.rafex.insightbloom.users.domain.services;

import dev.rafex.insightbloom.users.domain.model.Conference;
import dev.rafex.insightbloom.users.domain.model.DeviceBlock;
import dev.rafex.insightbloom.users.domain.model.ToolDeviceSession;
import dev.rafex.insightbloom.users.domain.model.ToolKind;
import dev.rafex.insightbloom.users.domain.ports.DeviceBlockRepository;
import dev.rafex.insightbloom.users.domain.ports.ToolDeviceSessionRepository;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Controla cuántos dispositivos distintos puede usar un mismo usuario en Jitsi/IDE dentro de un
 * evento, y cuántas cuentas distintas puede compartir un mismo dispositivo, antes de bloquearlo.
 * Ver spec-native/DECISIONS.md para el contexto completo de esta feature (2026-07).
 *
 * Comportamiento acordado con el organizador:
 * - Límite de dispositivos por usuario excedido -> se revoca automáticamente la sesión-dispositivo
 *   más vieja de ese usuario para esa herramienta (sin fricción, no se rechaza el login nuevo).
 * - "Dispositivo autorizado" = simplemente tener una sesión-dispositivo activa vigente.
 * - Umbral de cuentas por dispositivo excedido -> se bloquea el dispositivo para TODA la
 *   conferencia (ambas herramientas), y queda pendiente de revisión manual del moderador
 *   (ver ListDeviceBlocksUseCase/UnblockDeviceUseCase) -- evita penalizar de forma permanente
 *   compus compartidas legítimas de laboratorio.
 */
public class DeviceAccessGuard {

    private static final int DEFAULT_MAX_DEVICES_PER_USER = 2;
    private static final int DEFAULT_MAX_ACCOUNTS_PER_DEVICE = 3;

    public sealed interface DeviceAccessResult {
        record Allowed() implements DeviceAccessResult {}
        record Blocked(int accountCount) implements DeviceAccessResult {}
    }

    private final ToolDeviceSessionRepository sessionRepository;
    private final DeviceBlockRepository blockRepository;

    public DeviceAccessGuard(final ToolDeviceSessionRepository sessionRepository,
                              final DeviceBlockRepository blockRepository) {
        this.sessionRepository = sessionRepository;
        this.blockRepository = blockRepository;
    }

    public DeviceAccessResult checkAndRegister(final String conferenceUuid, final String userUuid,
                                                final ToolKind tool, final String deviceFingerprint,
                                                final Conference conference) {
        if (blockRepository.findActive(conferenceUuid, deviceFingerprint).isPresent()) {
            return new DeviceAccessResult.Blocked(0);
        }

        final var existing = sessionRepository.findActive(conferenceUuid, userUuid, tool, deviceFingerprint);
        if (existing.isPresent()) {
            sessionRepository.touch(existing.get().getUuid());
        } else {
            enforceDeviceLimit(conferenceUuid, userUuid, tool, conference);
            sessionRepository.save(new ToolDeviceSession(conferenceUuid, userUuid, tool, deviceFingerprint));
        }

        final int maxAccountsPerDevice = conference.getMaxAccountsPerDevice() != null
                ? conference.getMaxAccountsPerDevice() : DEFAULT_MAX_ACCOUNTS_PER_DEVICE;
        final List<ToolDeviceSession> deviceSessions = sessionRepository.findActiveByDevice(conferenceUuid, deviceFingerprint);
        final Set<String> distinctUsers = deviceSessions.stream()
                .map(ToolDeviceSession::getUserUuid)
                .collect(Collectors.toSet());
        if (distinctUsers.size() > maxAccountsPerDevice) {
            blockRepository.save(new DeviceBlock(conferenceUuid, deviceFingerprint, distinctUsers.size()));
            sessionRepository.revokeAllForDevice(conferenceUuid, deviceFingerprint);
            return new DeviceAccessResult.Blocked(distinctUsers.size());
        }

        return new DeviceAccessResult.Allowed();
    }

    private void enforceDeviceLimit(final String conferenceUuid, final String userUuid, final ToolKind tool,
                                     final Conference conference) {
        final int maxDevicesPerUser = conference.getMaxDevicesPerUser() != null
                ? conference.getMaxDevicesPerUser() : DEFAULT_MAX_DEVICES_PER_USER;
        final List<ToolDeviceSession> active = sessionRepository.findActiveByUserAndTool(conferenceUuid, userUuid, tool);
        if (active.size() >= maxDevicesPerUser) {
            // Ordenado ASC por first_seen_at (ver SqliteToolDeviceSessionRepository) -- el primero
            // de la lista es el dispositivo más viejo, se revoca para dejar espacio al nuevo.
            sessionRepository.revoke(active.get(0).getUuid());
        }
    }
}
