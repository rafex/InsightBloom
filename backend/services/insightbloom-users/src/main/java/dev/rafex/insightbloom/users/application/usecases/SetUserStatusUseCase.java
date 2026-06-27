package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.User;
import dev.rafex.insightbloom.users.domain.model.UserStatus;
import dev.rafex.insightbloom.users.domain.ports.UserRepository;
import dev.rafex.insightbloom.users.domain.services.TokenService;

/** Banear, reactivar o eliminar lógicamente una cuenta (acción de administrador). */
public class SetUserStatusUseCase {
    private final UserRepository userRepository;
    private final TokenService tokenService;

    public SetUserStatusUseCase(final UserRepository userRepository, final TokenService tokenService) {
        this.userRepository = userRepository;
        this.tokenService = tokenService;
    }

    public User execute(final String uuid, final UserStatus status) {
        final User user = userRepository.findByUuid(uuid)
                .orElseThrow(() -> new IllegalArgumentException("user_not_found"));
        user.setStatus(status);
        userRepository.save(user);
        if (status != UserStatus.ACTIVE) {
            // Banear o eliminar lógicamente cierra cualquier sesión activa de inmediato.
            tokenService.revokeAllForUser(uuid);
        }
        return user;
    }
}
