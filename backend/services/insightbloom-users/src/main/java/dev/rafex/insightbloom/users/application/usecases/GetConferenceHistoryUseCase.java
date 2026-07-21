package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.ports.ConferenceMembershipRepository;
import dev.rafex.insightbloom.users.domain.ports.ConferenceRepository;

import java.util.List;

public class GetConferenceHistoryUseCase {
    private final ConferenceMembershipRepository membershipRepository;
    private final ConferenceRepository conferenceRepository;

    public GetConferenceHistoryUseCase(final ConferenceMembershipRepository membershipRepository,
                                        final ConferenceRepository conferenceRepository) {
        this.membershipRepository = membershipRepository;
        this.conferenceRepository = conferenceRepository;
    }

    public record HistoryEntry(String conferenceUuid, String friendlyId, String name, boolean available,
                                String joinedAt, String seatingMode) {}

    public List<HistoryEntry> execute(final String userUuid) {
        return membershipRepository.findByUser(userUuid).stream()
                .map(m -> conferenceRepository.findByUuid(m.getConferenceUuid())
                        .map(c -> new HistoryEntry(c.getUuid(), c.getFriendlyId(), c.getName(), true,
                                m.getJoinedAt().toString(), c.getSeatingMode()))
                        .orElseGet(() -> new HistoryEntry(m.getConferenceUuid(), m.getConferenceFriendlyIdSnapshot(),
                                m.getConferenceNameSnapshot(), false, m.getJoinedAt().toString(), null)))
                .toList();
    }
}
