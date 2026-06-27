package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.User;
import dev.rafex.insightbloom.users.domain.model.UserRole;
import dev.rafex.insightbloom.users.domain.ports.UserRepository;

/** Edición de datos de cualquier usuario por un administrador. */
public class AdminUpdateUserUseCase {
    private final UserRepository userRepository;

    public AdminUpdateUserUseCase(final UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public record Request(String displayName, String email, String phone, String role,
                           String firstName, String lastName) {}

    public User execute(final String uuid, final Request req) {
        final User user = userRepository.findByUuid(uuid)
                .orElseThrow(() -> new IllegalArgumentException("user_not_found"));
        if (req.displayName() != null) user.setDisplayName(req.displayName());
        if (req.email() != null) user.setEmail(req.email());
        if (req.phone() != null) user.setPhone(req.phone());
        if (req.firstName() != null) user.setFirstName(req.firstName());
        if (req.lastName() != null) user.setLastName(req.lastName());
        if (req.role() != null) {
            try {
                user.setRole(UserRole.valueOf(req.role().toUpperCase()));
            } catch (final IllegalArgumentException e) {
                throw new IllegalArgumentException("invalid_role");
            }
        }
        userRepository.save(user);
        return user;
    }
}
