package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.ports.DeviceFingerprintFlagRepository;

/** Marca revisada una discrepancia de fingerprint -- nunca hubo bloqueo, solo la saca de la
 *  lista de pendientes en /dashboard/admin/device-access. */
public class ReviewDeviceFingerprintFlagUseCase {
    private final DeviceFingerprintFlagRepository repository;

    public ReviewDeviceFingerprintFlagUseCase(final DeviceFingerprintFlagRepository repository) {
        this.repository = repository;
    }

    public void execute(final String flagUuid, final String reviewedByUserUuid) {
        repository.markReviewed(flagUuid, reviewedByUserUuid);
    }
}
