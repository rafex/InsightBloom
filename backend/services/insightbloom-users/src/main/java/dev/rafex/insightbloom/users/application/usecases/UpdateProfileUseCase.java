package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.User;
import dev.rafex.insightbloom.users.domain.ports.UserRepository;

import java.util.Optional;

public class UpdateProfileUseCase {
    private final UserRepository userRepository;

    public UpdateProfileUseCase(final UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public record Request(String firstName, String lastName, String publicProfilePhotoBase64,
                           boolean updatePhoto) {}

    public Optional<GetUserProfileUseCase.Profile> execute(final String uuid, final Request request) {
        return userRepository.findByUuid(uuid).map(u -> {
            u.setFirstName(blankToNull(request.firstName()));
            u.setLastName(blankToNull(request.lastName()));
            if (request.updatePhoto()) {
                u.setPublicProfilePhotoBase64(ProfileImageNormalizer.normalize(request.publicProfilePhotoBase64()));
            }
            userRepository.save(u);
            return new GetUserProfileUseCase.Profile(u.getUuid(), u.getDisplayName(), u.getEmail(), u.getPhone(),
                    u.getFirstName(), u.getLastName(), u.getSocialLinks(), u.isEmailVerified(),
                    u.isPhoneVerified(), u.getPublicProfilePhotoBase64(), u.getAuthMethod().name());
        });
    }

    private static String blankToNull(final String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
