package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.ports.UserRepository;
import dev.rafex.insightbloom.users.domain.services.PasswordService;

public class ChangePasswordUseCase {
    private final UserRepository userRepository;
    private final PasswordService passwordService;

    public ChangePasswordUseCase(final UserRepository userRepository, final PasswordService passwordService) {
        this.userRepository = userRepository;
        this.passwordService = passwordService;
    }

    public record Request(String currentPassword, String newPassword) {}

    /** @return true if the password was changed, false if the user doesn't exist or the current password is wrong. */
    public boolean execute(final String uuid, final Request request) {
        if (request.newPassword() == null || request.newPassword().isBlank()) {
            throw new IllegalArgumentException("new_password_required");
        }
        return userRepository.findByUuid(uuid).map(u -> {
            final String currentHash = u.getPasswordHash();
            final boolean hasExistingPassword = currentHash != null && !currentHash.isBlank();
            if (hasExistingPassword) {
                if (request.currentPassword() == null
                        || !passwordService.verify(request.currentPassword(), currentHash)) {
                    return false;
                }
            }
            u.setPasswordHash(passwordService.hash(request.newPassword()));
            userRepository.save(u);
            return true;
        }).orElse(false);
    }
}
