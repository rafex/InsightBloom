package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.User;
import dev.rafex.insightbloom.users.domain.model.UserRole;
import dev.rafex.insightbloom.users.domain.ports.UserRepository;

import java.util.LinkedHashSet;
import java.util.Set;

/** Edición de datos de cualquier usuario por un administrador. */
public class AdminUpdateUserUseCase {
    private final UserRepository userRepository;

    public AdminUpdateUserUseCase(final UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** {@code roles} acepta un solo rol o una lista separada por comas, p.ej. "ORGANIZER,ADMIN". */
    public record Request(String displayName, String email, String phone, String roles,
                           String firstName, String lastName) {}

    public User execute(final String uuid, final Request req) {
        final User user = userRepository.findByUuid(uuid)
                .orElseThrow(() -> new IllegalArgumentException("user_not_found"));
        if (req.displayName() != null) user.setDisplayName(req.displayName());
        if (req.email() != null) user.setEmail(req.email());
        if (req.phone() != null) user.setPhone(req.phone());
        if (req.firstName() != null) user.setFirstName(req.firstName());
        if (req.lastName() != null) user.setLastName(req.lastName());
        if (req.roles() != null) {
            user.setRoles(parseRoles(req.roles()));
        }
        userRepository.save(user);
        return user;
    }

    private Set<UserRole> parseRoles(final String csv) {
        final Set<UserRole> roles = new LinkedHashSet<>();
        for (final String part : csv.split(",")) {
            final String trimmed = part.trim();
            if (trimmed.isEmpty()) continue;
            try {
                roles.add(UserRole.valueOf(trimmed.toUpperCase()));
            } catch (final IllegalArgumentException e) {
                throw new IllegalArgumentException("invalid_role");
            }
        }
        if (roles.isEmpty()) throw new IllegalArgumentException("invalid_role");
        return roles;
    }
}
