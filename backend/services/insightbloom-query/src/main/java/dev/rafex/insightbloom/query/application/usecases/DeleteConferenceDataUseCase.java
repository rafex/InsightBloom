package dev.rafex.insightbloom.query.application.usecases;

import dev.rafex.insightbloom.query.domain.ports.CloudWordRepository;
import dev.rafex.insightbloom.query.domain.ports.WordTimelineRepository;

public class DeleteConferenceDataUseCase {
    private final CloudWordRepository cloudRepo;
    private final WordTimelineRepository timelineRepo;

    public DeleteConferenceDataUseCase(final CloudWordRepository cloudRepo,
                                        final WordTimelineRepository timelineRepo) {
        this.cloudRepo = cloudRepo;
        this.timelineRepo = timelineRepo;
    }

    public void execute(final String conferenceUuid) {
        cloudRepo.deleteByConference(conferenceUuid);
        timelineRepo.deleteByConference(conferenceUuid);
    }
}
