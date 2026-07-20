package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.Conference;
import dev.rafex.insightbloom.users.domain.ports.ConferenceRepository;

/**
 * Configura, por evento, cuántos dispositivos distintos puede tener activos un usuario en
 * Jitsi/IDE y cuántas cuentas distintas puede compartir un dispositivo antes de bloquearlo --
 * ver DeviceAccessGuard.
 */
public class SetDeviceAccessConfigUseCase {
    private static final int MIN_DEVICES_PER_USER = 1;
    private static final int MAX_DEVICES_PER_USER = 10;
    private static final int MIN_ACCOUNTS_PER_DEVICE = 1;
    private static final int MAX_ACCOUNTS_PER_DEVICE = 50;

    private final ConferenceRepository conferenceRepository;

    public SetDeviceAccessConfigUseCase(final ConferenceRepository conferenceRepository) {
        this.conferenceRepository = conferenceRepository;
    }

    public Conference execute(final String conferenceUuid, final Integer maxDevicesPerUser,
                               final Integer maxAccountsPerDevice) {
        final Conference conf = conferenceRepository.findByUuid(conferenceUuid)
                .orElseThrow(() -> new IllegalArgumentException("conference_not_found"));

        if (maxDevicesPerUser != null
                && (maxDevicesPerUser < MIN_DEVICES_PER_USER || maxDevicesPerUser > MAX_DEVICES_PER_USER)) {
            throw new IllegalArgumentException("max_devices_per_user_out_of_range");
        }
        if (maxAccountsPerDevice != null
                && (maxAccountsPerDevice < MIN_ACCOUNTS_PER_DEVICE || maxAccountsPerDevice > MAX_ACCOUNTS_PER_DEVICE)) {
            throw new IllegalArgumentException("max_accounts_per_device_out_of_range");
        }

        conf.setMaxDevicesPerUser(maxDevicesPerUser);
        conf.setMaxAccountsPerDevice(maxAccountsPerDevice);
        conferenceRepository.save(conf);
        return conf;
    }
}
