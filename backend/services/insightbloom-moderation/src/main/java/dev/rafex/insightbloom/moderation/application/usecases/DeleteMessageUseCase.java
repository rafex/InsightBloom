package dev.rafex.insightbloom.moderation.application.usecases;

import dev.rafex.insightbloom.moderation.domain.model.ModerationMessage;
import dev.rafex.insightbloom.moderation.domain.ports.ModerationMessageRepository;
import dev.rafex.insightbloom.moderation.domain.ports.QueryPort;

public class DeleteMessageUseCase {
    private final ModerationMessageRepository repo;
    private final QueryPort queryPort;

    public DeleteMessageUseCase(ModerationMessageRepository repo, QueryPort queryPort) {
        this.repo = repo;
        this.queryPort = queryPort;
    }

    public record Request(String messageUuid, String deletedByUserUuid) {}

    public void execute(Request req) {
        ModerationMessage msg = repo.findByUuid(req.messageUuid())
            .or(() -> repo.findByMessageUuid(req.messageUuid()))
            .orElseThrow(() -> new IllegalArgumentException("message_not_found"));
        msg.delete(req.deletedByUserUuid());
        repo.save(msg);
        try { queryPort.setMessageVisibility(msg.getMessageUuid(), false); } catch (Exception ignored) {}
    }
}
