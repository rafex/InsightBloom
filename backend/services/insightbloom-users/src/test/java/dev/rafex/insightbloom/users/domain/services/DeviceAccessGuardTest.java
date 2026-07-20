package dev.rafex.insightbloom.users.domain.services;

import dev.rafex.insightbloom.users.domain.model.Conference;
import dev.rafex.insightbloom.users.domain.model.DeviceBlock;
import dev.rafex.insightbloom.users.domain.model.ToolDeviceSession;
import dev.rafex.insightbloom.users.domain.model.ToolKind;
import dev.rafex.insightbloom.users.domain.ports.DeviceBlockRepository;
import dev.rafex.insightbloom.users.domain.ports.ToolDeviceSessionRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class DeviceAccessGuardTest {

    @Test
    void exceedingDeviceLimit_revokesOldestSessionAndAllowsNewOne() {
        final ToolDeviceSessionRepository sessionRepo = Mockito.mock(ToolDeviceSessionRepository.class);
        final DeviceBlockRepository blockRepo = Mockito.mock(DeviceBlockRepository.class);
        final var guard = new DeviceAccessGuard(sessionRepo, blockRepo);
        final Conference conference = new Conference("evt1", "Evento", "org-1");
        conference.setMaxDevicesPerUser(2);
        conference.setMaxAccountsPerDevice(50);

        final ToolDeviceSession oldest = new ToolDeviceSession("conf-1", "user-1", ToolKind.IDE, "fp-old");
        final ToolDeviceSession newer = new ToolDeviceSession("conf-1", "user-1", ToolKind.IDE, "fp-mid");

        Mockito.when(blockRepo.findActive("conf-1", "fp-new")).thenReturn(Optional.empty());
        Mockito.when(sessionRepo.findActive("conf-1", "user-1", ToolKind.IDE, "fp-new")).thenReturn(Optional.empty());
        // Ya al limite (2 dispositivos activos) cuando llega un tercero -- se espera que se
        // revoque el mas viejo (primero de la lista, ver contrato de ordenamiento ASC).
        Mockito.when(sessionRepo.findActiveByUserAndTool("conf-1", "user-1", ToolKind.IDE))
                .thenReturn(List.of(oldest, newer));
        Mockito.when(sessionRepo.findActiveByDevice("conf-1", "fp-new"))
                .thenReturn(List.of(new ToolDeviceSession("conf-1", "user-1", ToolKind.IDE, "fp-new")));

        final var result = guard.checkAndRegister("conf-1", "user-1", ToolKind.IDE, "fp-new", conference);

        assertInstanceOf(DeviceAccessGuard.DeviceAccessResult.Allowed.class, result);
        Mockito.verify(sessionRepo).revoke(oldest.getUuid());
        Mockito.verify(sessionRepo, Mockito.never()).revoke(newer.getUuid());
        final ArgumentCaptor<ToolDeviceSession> saved = ArgumentCaptor.forClass(ToolDeviceSession.class);
        Mockito.verify(sessionRepo).save(saved.capture());
        assertEquals("fp-new", saved.getValue().getDeviceFingerprint());
    }

    @Test
    void exceedingAccountThreshold_blocksDeviceAndRevokesAllItsSessions() {
        final ToolDeviceSessionRepository sessionRepo = Mockito.mock(ToolDeviceSessionRepository.class);
        final DeviceBlockRepository blockRepo = Mockito.mock(DeviceBlockRepository.class);
        final var guard = new DeviceAccessGuard(sessionRepo, blockRepo);
        final Conference conference = new Conference("evt1", "Evento", "org-1");
        conference.setMaxDevicesPerUser(10);
        conference.setMaxAccountsPerDevice(2);

        Mockito.when(blockRepo.findActive("conf-1", "shared-fp")).thenReturn(Optional.empty());
        Mockito.when(sessionRepo.findActive("conf-1", "user-3", ToolKind.VIDEO, "shared-fp")).thenReturn(Optional.empty());
        Mockito.when(sessionRepo.findActiveByUserAndTool("conf-1", "user-3", ToolKind.VIDEO)).thenReturn(List.of());
        // 3 cuentas distintas ya comparten este fingerprint -- supera maxAccountsPerDevice=2.
        Mockito.when(sessionRepo.findActiveByDevice("conf-1", "shared-fp")).thenReturn(List.of(
                new ToolDeviceSession("conf-1", "user-1", ToolKind.VIDEO, "shared-fp"),
                new ToolDeviceSession("conf-1", "user-2", ToolKind.VIDEO, "shared-fp"),
                new ToolDeviceSession("conf-1", "user-3", ToolKind.VIDEO, "shared-fp")
        ));

        final var result = guard.checkAndRegister("conf-1", "user-3", ToolKind.VIDEO, "shared-fp", conference);

        assertInstanceOf(DeviceAccessGuard.DeviceAccessResult.Blocked.class, result);
        assertEquals(3, ((DeviceAccessGuard.DeviceAccessResult.Blocked) result).accountCount());
        Mockito.verify(blockRepo).save(Mockito.any(DeviceBlock.class));
        Mockito.verify(sessionRepo).revokeAllForDevice("conf-1", "shared-fp");
    }

    @Test
    void alreadyBlockedDevice_isRejectedWithoutCountingAgain() {
        final ToolDeviceSessionRepository sessionRepo = Mockito.mock(ToolDeviceSessionRepository.class);
        final DeviceBlockRepository blockRepo = Mockito.mock(DeviceBlockRepository.class);
        final var guard = new DeviceAccessGuard(sessionRepo, blockRepo);
        final Conference conference = new Conference("evt1", "Evento", "org-1");

        Mockito.when(blockRepo.findActive("conf-1", "blocked-fp"))
                .thenReturn(Optional.of(new DeviceBlock("conf-1", "blocked-fp", 5)));

        final var result = guard.checkAndRegister("conf-1", "user-9", ToolKind.IDE, "blocked-fp", conference);

        assertInstanceOf(DeviceAccessGuard.DeviceAccessResult.Blocked.class, result);
        Mockito.verify(sessionRepo, Mockito.never()).findActive(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
        Mockito.verify(sessionRepo, Mockito.never()).save(Mockito.any());
        Mockito.verify(blockRepo, Mockito.never()).save(Mockito.any());
    }

    @Test
    void reconnectingSameDevice_onlyTouchesExistingSession() {
        final ToolDeviceSessionRepository sessionRepo = Mockito.mock(ToolDeviceSessionRepository.class);
        final DeviceBlockRepository blockRepo = Mockito.mock(DeviceBlockRepository.class);
        final var guard = new DeviceAccessGuard(sessionRepo, blockRepo);
        final Conference conference = new Conference("evt1", "Evento", "org-1");

        final ToolDeviceSession existing = new ToolDeviceSession("conf-1", "user-1", ToolKind.IDE, "fp-1");
        Mockito.when(blockRepo.findActive("conf-1", "fp-1")).thenReturn(Optional.empty());
        Mockito.when(sessionRepo.findActive("conf-1", "user-1", ToolKind.IDE, "fp-1")).thenReturn(Optional.of(existing));
        Mockito.when(sessionRepo.findActiveByDevice("conf-1", "fp-1")).thenReturn(List.of(existing));

        final var result = guard.checkAndRegister("conf-1", "user-1", ToolKind.IDE, "fp-1", conference);

        assertInstanceOf(DeviceAccessGuard.DeviceAccessResult.Allowed.class, result);
        Mockito.verify(sessionRepo).touch(existing.getUuid());
        Mockito.verify(sessionRepo, Mockito.never()).save(Mockito.any());
        Mockito.verify(sessionRepo, Mockito.never()).revoke(Mockito.any());
    }
}
