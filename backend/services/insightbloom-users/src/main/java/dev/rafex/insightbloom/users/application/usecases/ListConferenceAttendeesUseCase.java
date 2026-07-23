package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.ports.ConferenceMembershipRepository;
import dev.rafex.insightbloom.users.domain.ports.UserRepository;

import java.util.List;

/** Lista las cuentas que se registraron/entraron a una conferencia. */
public class ListConferenceAttendeesUseCase {
    private final ConferenceMembershipRepository membershipRepository;
    private final UserRepository userRepository;

    public ListConferenceAttendeesUseCase(final ConferenceMembershipRepository membershipRepository,
                                          final UserRepository userRepository) {
        this.membershipRepository = membershipRepository;
        this.userRepository = userRepository;
    }

    public List<Attendee> execute(final String conferenceUuid) {
        return membershipRepository.findByConference(conferenceUuid).stream()
                .map(membership -> userRepository.findByUuid(membership.getUserUuid())
                        .map(user -> new Attendee(user.getUuid(), user.getDisplayName(), user.getEmail(),
                                membership.getJoinedAt().toString()))
                        .orElse(null))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    public record Attendee(String uuid, String displayName, String email, String joinedAt) {}
}
