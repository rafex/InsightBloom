package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.PlatformSettings;
import dev.rafex.insightbloom.users.domain.ports.PlatformSettingsRepository;

/** Umbrales de PlatformDeviceGuard, configurables desde /dashboard/admin/device-access. */
public class SetDeviceAccessSettingsUseCase {
    private static final int MIN_ACCOUNTS_PER_DEVICE = 1;
    private static final int MAX_ACCOUNTS_PER_DEVICE = 50;
    private static final int MIN_SESSIONS_PER_USER = 1;
    private static final int MAX_SESSIONS_PER_USER = 20;
    private static final int MIN_REGISTRATIONS_PER_DAY = 1;
    private static final int MAX_REGISTRATIONS_PER_DAY = 50;

    private final PlatformSettingsRepository repository;

    public SetDeviceAccessSettingsUseCase(final PlatformSettingsRepository repository) {
        this.repository = repository;
    }

    public PlatformSettings execute(final Integer maxAccountsPerDevice, final Integer maxSessionsPerUser,
                                     final Integer maxRegistrationsPerDevicePerDay) {
        if (maxAccountsPerDevice != null
                && (maxAccountsPerDevice < MIN_ACCOUNTS_PER_DEVICE || maxAccountsPerDevice > MAX_ACCOUNTS_PER_DEVICE)) {
            throw new IllegalArgumentException("max_accounts_per_device_out_of_range");
        }
        if (maxSessionsPerUser != null
                && (maxSessionsPerUser < MIN_SESSIONS_PER_USER || maxSessionsPerUser > MAX_SESSIONS_PER_USER)) {
            throw new IllegalArgumentException("max_sessions_per_user_out_of_range");
        }
        if (maxRegistrationsPerDevicePerDay != null
                && (maxRegistrationsPerDevicePerDay < MIN_REGISTRATIONS_PER_DAY
                    || maxRegistrationsPerDevicePerDay > MAX_REGISTRATIONS_PER_DAY)) {
            throw new IllegalArgumentException("max_registrations_per_device_per_day_out_of_range");
        }

        final PlatformSettings s = repository.get();
        s.setMaxAccountsPerDevice(maxAccountsPerDevice);
        s.setMaxSessionsPerUser(maxSessionsPerUser);
        s.setMaxRegistrationsPerDevicePerDay(maxRegistrationsPerDevicePerDay);
        repository.save(s);
        return s;
    }
}
