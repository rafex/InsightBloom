package dev.rafex.insightbloom.moderation.application.usecases;

import dev.rafex.insightbloom.moderation.domain.model.ModerationMessage;
import dev.rafex.insightbloom.moderation.domain.ports.ModerationMessageRepository;

public class EditMessageUseCase {
    private final ModerationMessageRepository repo;

    public EditMessageUseCase(ModerationMessageRepository repo) { this.repo = repo; }

    public record Request(String messageUuid, String editedWord, String editedDetail, String updatedByUserUuid) {}

    public void execute(Request req) {
        ModerationMessage msg = repo.findByUuid(req.messageUuid())
            .orElseThrow(() -> new IllegalArgumentException("message_not_found"));
        msg.edit(req.editedWord(), req.editedDetail(), req.updatedByUserUuid());
        repo.save(msg);
    }
}
