package dev.rafex.insightbloom.users.domain.services;

import dev.rafex.insightbloom.users.domain.ports.DeviceFingerprintFlagRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class DeviceFingerprintAuditorTest {

    @Test
    void mismatch_recordsFlag() {
        final DeviceFingerprintFlagRepository repo = Mockito.mock(DeviceFingerprintFlagRepository.class);
        final var auditor = new DeviceFingerprintAuditor(repo);

        auditor.audit("token-1", "user-1", "user", "fp-login", "fp-different");

        Mockito.verify(repo).recordMismatch("token-1", "user-1", "user", "fp-login", "fp-different");
    }

    @Test
    void repeatedMismatchOnSameToken_delegatesToRepositoryUpsertEachTime() {
        // El upsert (incrementar occurrence_count en vez de duplicar fila) vive en el
        // repositorio (ver SqliteDeviceFingerprintFlagRepository, ON CONFLICT(token_uuid)) --
        // el auditor solo debe llamar recordMismatch en cada deteccion, sin de-duplicar el mismo.
        final DeviceFingerprintFlagRepository repo = Mockito.mock(DeviceFingerprintFlagRepository.class);
        final var auditor = new DeviceFingerprintAuditor(repo);

        auditor.audit("token-1", "user-1", "user", "fp-login", "fp-different");
        auditor.audit("token-1", "user-1", "user", "fp-login", "fp-different");

        Mockito.verify(repo, Mockito.times(2))
                .recordMismatch("token-1", "user-1", "user", "fp-login", "fp-different");
    }

    @Test
    void matchingFingerprint_doesNothing() {
        final DeviceFingerprintFlagRepository repo = Mockito.mock(DeviceFingerprintFlagRepository.class);
        final var auditor = new DeviceFingerprintAuditor(repo);

        auditor.audit("token-1", "user-1", "user", "fp-same", "fp-same");

        Mockito.verifyNoInteractions(repo);
    }

    @Test
    void nullOrBlankRequestFingerprint_doesNothing() {
        final DeviceFingerprintFlagRepository repo = Mockito.mock(DeviceFingerprintFlagRepository.class);
        final var auditor = new DeviceFingerprintAuditor(repo);

        auditor.audit("token-1", "user-1", "user", "fp-login", null);
        auditor.audit("token-1", "user-1", "user", "fp-login", "   ");

        Mockito.verifyNoInteractions(repo);
    }

    @Test
    void nullOrBlankLoginFingerprint_doesNothing() {
        // Sesiones viejas (antes de este cambio) pueden no tener login_fingerprint guardado --
        // no hay nada contra que comparar, no se debe generar ruido de "mismatch" falso.
        final DeviceFingerprintFlagRepository repo = Mockito.mock(DeviceFingerprintFlagRepository.class);
        final var auditor = new DeviceFingerprintAuditor(repo);

        auditor.audit("token-1", "user-1", "user", null, "fp-request");
        auditor.audit("token-1", "user-1", "user", "", "fp-request");

        Mockito.verifyNoInteractions(repo);
    }
}
