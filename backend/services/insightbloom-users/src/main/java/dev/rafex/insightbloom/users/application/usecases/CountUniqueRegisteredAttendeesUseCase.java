package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.ports.ConferenceMembershipRepository;
import dev.rafex.insightbloom.users.domain.ports.ConferenceRepository;

import java.util.List;

/**
 * Cuenta usuarios únicos (no memberships) que se unieron a alguna conferencia del organizador.
 * Distinto de {@link CountRegisteredAttendeesUseCase}, que cuenta por conferencia individual y
 * por lo tanto duplica a quienes se unieron a más de una.
 */
public class CountUniqueRegisteredAttendeesUseCase {
    private final ConferenceRepository conferenceRepository;
    private final ConferenceMembershipRepository membershipRepository;

    public CountUniqueRegisteredAttendeesUseCase(final ConferenceRepository conferenceRepository,
                                                  final ConferenceMembershipRepository membershipRepository) {
        this.conferenceRepository = conferenceRepository;
        this.membershipRepository = membershipRepository;
    }

    public long execute(final String organizerUserUuid) {
        final List<String> conferenceUuids = conferenceRepository.findByUser(organizerUserUuid).stream()
                .map(dev.rafex.insightbloom.users.domain.model.Conference::getUuid)
                .toList();
        return membershipRepository.countDistinctUsersByConferences(conferenceUuids);
    }
}
