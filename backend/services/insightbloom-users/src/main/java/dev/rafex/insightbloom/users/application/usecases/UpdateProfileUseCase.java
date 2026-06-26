package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.User;
import dev.rafex.insightbloom.users.domain.ports.UserRepository;

import java.util.Optional;

public class UpdateProfileUseCase {
    private final UserRepository userRepository;

    public UpdateProfileUseCase(final UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public record Request(String firstName, String lastName) {}

    public Optional<GetUserProfileUseCase.Profile> execute(final String uuid, final Request request) {
        return userRepository.findByUuid(uuid).map(u -> {
            u.setFirstName(blankToNull(request.firstName()));
            u.setLastName(blankToNull(request.lastName()));
            userRepository.save(u);
            return new GetUserProfileUseCase.Profile(u.getUuid(), u.getDisplayName(), u.getEmail(), u.getPhone(),
                    u.getFirstName(), u.getLastName());
        });
    }

    private static String blankToNull(final String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
