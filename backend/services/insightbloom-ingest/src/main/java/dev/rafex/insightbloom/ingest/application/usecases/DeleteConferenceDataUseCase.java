package dev.rafex.insightbloom.ingest.application.usecases;

import dev.rafex.insightbloom.ingest.domain.ports.MessageRepository;

public class DeleteConferenceDataUseCase {
    private final MessageRepository messageRepo;

    public DeleteConferenceDataUseCase(final MessageRepository messageRepo) {
        this.messageRepo = messageRepo;
    }

    public void execute(final String conferenceUuid) {
        messageRepo.deleteByConference(conferenceUuid);
    }
}
