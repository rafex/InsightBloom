package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.SocialLink;
import dev.rafex.insightbloom.users.domain.model.User;
import dev.rafex.insightbloom.users.domain.model.UserRole;
import dev.rafex.insightbloom.users.domain.ports.UserRepository;

import java.util.List;
import java.util.UUID;

public class RegisterUseCase {
    private final UserRepository userRepository;

    public RegisterUseCase(final UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public record Request(String displayName, String email, String phone, String password,
                           List<SocialLink> socialLinks) {}

    public User execute(final Request req) {
        final boolean hasEmail = req.email() != null && !req.email().isBlank();
        final boolean hasPhone = req.phone() != null && !req.phone().isBlank();
        if (!hasEmail && !hasPhone) {
            throw new IllegalArgumentException("email_or_phone_required");
        }
        if (req.password() == null || req.password().isBlank()) {
            throw new IllegalArgumentException("password_required");
        }
        if (hasEmail && userRepository.findByEmail(req.email()).isPresent()) {
            throw new IllegalArgumentException("email_already_registered");
        }
        if (hasPhone && userRepository.findByPhone(req.phone()).isPresent()) {
            throw new IllegalArgumentException("phone_already_registered");
        }

        final String username = hasEmail ? req.email() : req.phone();
        final User user = new User(UUID.randomUUID().toString(), username, req.displayName(),
                hasEmail ? req.email() : null, hasPhone ? req.phone() : null,
                req.socialLinks(), false, false, UserRole.ATTENDEE);
        user.setPasswordHash(LoginUseCase.sha256(req.password()));
        userRepository.save(user);
        return user;
    }
}
