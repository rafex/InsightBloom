package dev.rafex.insightbloom.users.domain.services;

import dev.rafex.insightbloom.users.domain.ports.DeviceFingerprintFlagRepository;

/**
 * Auditoria (NO bloqueo) de fingerprint por request: compara la huella que llega en un request
 * autenticado contra la que se guardo al emitir el token en el login, y si no coincide, deja un
 * registro para que un system_admin lo revise en /dashboard/admin/device-access.
 *
 * Deliberadamente nunca corta la sesion -- ThumbmarkJS puede cambiar legitimamente entre
 * requests (navegadores con proteccion de privacidad randomizan canvas/audio/WebGL a proposito,
 * actualizaciones de navegador, etc.), asi que un mismatch es solo una senal a revisar, no una
 * prueba de robo de sesion. Ver DeviceFingerprintAuditHandler, el adapter Jetty que llama esto en
 * cada request.
 */
public class DeviceFingerprintAuditor {
    private final DeviceFingerprintFlagRepository flagRepository;

    public DeviceFingerprintAuditor(final DeviceFingerprintFlagRepository flagRepository) {
        this.flagRepository = flagRepository;
    }

    public void audit(final String tokenUuid, final String subjectUuid, final String subjectKind,
                       final String loginFingerprint, final String requestFingerprint) {
        if (requestFingerprint == null || requestFingerprint.isBlank()) return;
        if (loginFingerprint == null || loginFingerprint.isBlank()) return;
        if (requestFingerprint.equals(loginFingerprint)) return;

        flagRepository.recordMismatch(tokenUuid, subjectUuid, subjectKind, loginFingerprint, requestFingerprint);
    }
}
