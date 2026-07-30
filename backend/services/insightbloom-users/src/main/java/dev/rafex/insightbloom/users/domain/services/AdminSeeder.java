package dev.rafex.insightbloom.users.domain.services;

import dev.rafex.insightbloom.users.domain.model.User;
import dev.rafex.insightbloom.users.domain.model.UserRole;
import dev.rafex.insightbloom.users.domain.ports.UserRepository;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Crea el usuario administrador de la plataforma, una sola vez, si no existe en la base de
 * datos al iniciar. Las credenciales provienen de las variables de entorno {@code ADMIN_USERNAME}
 * y {@code ADMIN_PASSWORD}, inyectadas desde el secreto SOPS {@code insightbloom-admin-auth}.
 *
 * Idempotente: si el usuario ya existe no lo toca, sin importar el valor actual de las env vars.
 * Si las env vars no están definidas o están en blanco, no hace nada (backward compatible).
 *
 * Usa PBKDF2 para el hash de la contraseña, igual que el registro por API
 * ({@link PasswordService#hash(String)}).
 */
public final class AdminSeeder {
    private AdminSeeder() {}

    public static void seedIfNeeded(final UserRepository userRepo, final PasswordService passwordService,
                                     final String adminUsername, final String adminPassword) {
        if (adminUsername == null || adminUsername.isBlank()) return;
        if (adminPassword == null || adminPassword.isBlank()) return;

        if (userRepo.findByUsername(adminUsername).isPresent()) return;

        final var user = new User(UUID.randomUUID().toString(), adminUsername, "Admin",
                null, null, List.of(), false, false,
                Set.of(UserRole.ADMIN, UserRole.ORGANIZER));
        user.setPasswordHash(passwordService.hash(adminPassword));
        userRepo.save(user);
        System.out.println("AdminSeeder: admin user created — " + adminUsername);
    }
}
