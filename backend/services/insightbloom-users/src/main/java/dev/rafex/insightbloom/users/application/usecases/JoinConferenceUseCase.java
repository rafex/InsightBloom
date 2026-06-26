package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.Conference;
import dev.rafex.insightbloom.users.domain.model.ConferenceMembership;
import dev.rafex.insightbloom.users.domain.ports.ConferenceMembershipRepository;

public class JoinConferenceUseCase {
    private final GetConferenceUseCase getConferenceUseCase;
    private final ConferenceMembershipRepository membershipRepository;

    public JoinConferenceUseCase(final GetConferenceUseCase getConferenceUseCase,
                                  final ConferenceMembershipRepository membershipRepository) {
        this.getConferenceUseCase = getConferenceUseCase;
        this.membershipRepository = membershipRepository;
    }

    public Conference execute(final String userUuid, final String identifier) {
        final Conference conference = getConferenceUseCase.resolveAny(identifier)
                .orElseThrow(() -> new IllegalArgumentException("conference_not_found"));
        membershipRepository.recordJoin(new ConferenceMembership(
                userUuid, conference.getUuid(), conference.getName(), conference.getFriendlyId()));
        return conference;
    }
}
