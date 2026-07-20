package dev.rafex.insightbloom.users.domain.services;

/**
 * Lanzada cuando {@link PlatformDeviceGuard} determina que el dispositivo esta bloqueado a nivel
 * PLATAFORMA (no solo un evento puntual) -- no puede ni registrarse ni iniciar sesion hasta que
 * un system_admin lo desbloquee. Los handlers HTTP la mapean a 403 {@code platform_device_blocked}.
 */
public class PlatformDeviceBlockedException extends RuntimeException {
    public PlatformDeviceBlockedException() {
        super("platform_device_blocked");
    }
}
