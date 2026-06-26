package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.User;
import dev.rafex.insightbloom.users.domain.ports.UserRepository;

import java.util.Optional;

public class GetUserProfileUseCase {
    private final UserRepository userRepository;

    public GetUserProfileUseCase(final UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public record Profile(String uuid, String displayName, String email, String phone,
                          String firstName, String lastName) {}

    public Optional<Profile> execute(final String uuid) {
        return userRepository.findByUuid(uuid)
                .map(u -> new Profile(u.getUuid(), u.getDisplayName(), u.getEmail(), u.getPhone(),
                        u.getFirstName(), u.getLastName()));
    }
}
