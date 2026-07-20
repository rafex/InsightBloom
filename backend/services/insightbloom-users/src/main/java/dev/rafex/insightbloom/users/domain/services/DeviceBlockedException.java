package dev.rafex.insightbloom.users.domain.services;

/**
 * Lanzada cuando {@link DeviceAccessGuard} determina que el dispositivo que hace el request está
 * bloqueado para esta conferencia (demasiadas cuentas distintas desde el mismo fingerprint). Los
 * handlers HTTP la mapean a 403 {@code device_blocked}.
 */
public class DeviceBlockedException extends RuntimeException {
    public DeviceBlockedException() {
        super("device_blocked");
    }
}
