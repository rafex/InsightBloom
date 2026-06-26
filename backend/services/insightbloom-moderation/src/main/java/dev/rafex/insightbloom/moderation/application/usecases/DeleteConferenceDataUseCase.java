package dev.rafex.insightbloom.moderation.application.usecases;

import dev.rafex.insightbloom.moderation.domain.ports.ModerationMessageRepository;
import dev.rafex.insightbloom.moderation.domain.ports.ModerationWordRepository;

public class DeleteConferenceDataUseCase {
    private final ModerationWordRepository wordRepo;
    private final ModerationMessageRepository messageRepo;

    public DeleteConferenceDataUseCase(final ModerationWordRepository wordRepo,
                                        final ModerationMessageRepository messageRepo) {
        this.wordRepo = wordRepo;
        this.messageRepo = messageRepo;
    }

    public void execute(final String conferenceUuid) {
        wordRepo.deleteByConference(conferenceUuid);
        messageRepo.deleteByConference(conferenceUuid);
    }
}
