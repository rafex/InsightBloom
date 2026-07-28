package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.AuthMethod;
import dev.rafex.insightbloom.users.domain.ports.UserRepository;
import dev.rafex.insightbloom.users.domain.services.PasswordService;

/**
 * Cambia el metodo de acceso de la cuenta (perfil). Exige confirmar la contrasena actual en
 * AMBAS direcciones (activar OTP y volver a password) -- es un cambio de seguridad de la
 * cuenta, no una preferencia cosmetica: si una sesion robada pudiera desactivar el password sin
 * conocerlo, seria una puerta trasera permanente.
 */
public class SetAuthMethodUseCase {
    private final UserRepository userRepository;
    private final PasswordService passwordService;

    public SetAuthMethodUseCase(final UserRepository userRepository, final PasswordService passwordService) {
        this.userRepository = userRepository;
        this.passwordService = passwordService;
    }

    public record Request(String currentPassword, AuthMethod newMethod) {}

    /** @return true si se aplico el cambio, false si el usuario no existe o la contrasena actual es incorrecta. */
    public boolean execute(final String uuid, final Request request) {
        if (request == null || request.newMethod() == null) {
            throw new IllegalArgumentException("auth_method_required");
        }
        return userRepository.findByUuid(uuid).map(u -> {
            final String currentHash = u.getPasswordHash();
            if (currentHash == null || currentHash.isBlank()) return false;
            if (request.currentPassword() == null || !passwordService.verify(request.currentPassword(), currentHash)) {
                return false;
            }
            u.setAuthMethod(request.newMethod());
            userRepository.save(u);
            return true;
        }).orElse(false);
    }
}
