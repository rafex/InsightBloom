package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.ports.UserRepository;

public class ChangePasswordUseCase {
    private final UserRepository userRepository;

    public ChangePasswordUseCase(final UserRepository userRepository) {
        this.userRepository = userRepository;
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
                        || !LoginUseCase.sha256(request.currentPassword()).equals(currentHash)) {
                    return false;
                }
            }
            u.setPasswordHash(LoginUseCase.sha256(request.newPassword()));
            userRepository.save(u);
            return true;
        }).orElse(false);
    }
}
