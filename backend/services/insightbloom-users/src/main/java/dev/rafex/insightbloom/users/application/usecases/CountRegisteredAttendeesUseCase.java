package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.ports.ConferenceMembershipRepository;

public class CountRegisteredAttendeesUseCase {
    private final ConferenceMembershipRepository membershipRepository;

    public CountRegisteredAttendeesUseCase(final ConferenceMembershipRepository membershipRepository) {
        this.membershipRepository = membershipRepository;
    }

    public long execute(final String conferenceUuid) {
        return membershipRepository.countByConference(conferenceUuid);
    }
}
