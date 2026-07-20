package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.SocialLink;
import dev.rafex.insightbloom.users.domain.model.User;
import dev.rafex.insightbloom.users.domain.model.UserRole;
import dev.rafex.insightbloom.users.domain.ports.PlatformSettingsRepository;
import dev.rafex.insightbloom.users.domain.ports.UserRepository;
import dev.rafex.insightbloom.users.domain.services.PasswordService;
import dev.rafex.insightbloom.users.domain.services.PlatformDeviceBlockedException;
import dev.rafex.insightbloom.users.domain.services.PlatformDeviceGuard;

import java.util.List;
import java.util.UUID;

public class RegisterUseCase {
    private final UserRepository userRepository;
    private final PasswordService passwordService;
    private final PlatformDeviceGuard platformDeviceGuard;
    private final PlatformSettingsRepository platformSettingsRepository;

    public RegisterUseCase(final UserRepository userRepository, final PasswordService passwordService,
                            final PlatformDeviceGuard platformDeviceGuard,
                            final PlatformSettingsRepository platformSettingsRepository) {
        this.userRepository = userRepository;
        this.passwordService = passwordService;
        this.platformDeviceGuard = platformDeviceGuard;
        this.platformSettingsRepository = platformSettingsRepository;
    }

    public record Request(String displayName, String email, String phone, String password,
                           List<SocialLink> socialLinks, String deviceFingerprint) {}

    public User execute(final Request req) {
        final var access = platformDeviceGuard.checkRegistration(req.deviceFingerprint(), platformSettingsRepository.get());
        if (access instanceof PlatformDeviceGuard.Result.Blocked) {
            throw new PlatformDeviceBlockedException();
        }

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
        user.setPasswordHash(passwordService.hash(req.password()));
        user.setRegistrationDeviceFingerprint(req.deviceFingerprint());
        userRepository.save(user);
        return user;
    }
}
