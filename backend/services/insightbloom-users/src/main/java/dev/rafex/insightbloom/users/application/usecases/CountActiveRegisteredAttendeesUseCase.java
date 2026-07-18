package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.ports.ConferenceMembershipRepository;
import dev.rafex.insightbloom.users.domain.ports.ConferenceRepository;

import java.util.List;

/**
 * Igual que {@link CountUniqueRegisteredAttendeesUseCase}, pero solo cuenta usuarios cuyo
 * status es ACTIVE (excluye INACTIVE/BANNED/DELETED).
 */
public class CountActiveRegisteredAttendeesUseCase {
    private final ConferenceRepository conferenceRepository;
    private final ConferenceMembershipRepository membershipRepository;

    public CountActiveRegisteredAttendeesUseCase(final ConferenceRepository conferenceRepository,
                                                   final ConferenceMembershipRepository membershipRepository) {
        this.conferenceRepository = conferenceRepository;
        this.membershipRepository = membershipRepository;
    }

    public long execute(final String organizerUserUuid) {
        final List<String> conferenceUuids = conferenceRepository.findByUser(organizerUserUuid).stream()
                .map(dev.rafex.insightbloom.users.domain.model.Conference::getUuid)
                .toList();
        return membershipRepository.countDistinctActiveUsersByConferences(conferenceUuids);
    }
}
