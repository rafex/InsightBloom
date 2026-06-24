package dev.rafex.insightbloom.moderation.application.usecases;

import dev.rafex.insightbloom.moderation.domain.model.ModerationMessage;
import dev.rafex.insightbloom.moderation.domain.ports.ModerationMessageRepository;

public class DeleteMessageUseCase {
    private final ModerationMessageRepository repo;

    public DeleteMessageUseCase(ModerationMessageRepository repo) { this.repo = repo; }

    public record Request(String messageUuid, String deletedByUserUuid) {}

    public void execute(Request req) {
        ModerationMessage msg = repo.findByUuid(req.messageUuid())
            .or(() -> repo.findByMessageUuid(req.messageUuid()))
            .orElseThrow(() -> new IllegalArgumentException("message_not_found"));
        msg.delete(req.deletedByUserUuid());
        repo.save(msg);
    }
}
