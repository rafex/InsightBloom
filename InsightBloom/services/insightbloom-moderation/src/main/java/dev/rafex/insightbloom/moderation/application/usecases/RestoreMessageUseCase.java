package dev.rafex.insightbloom.moderation.application.usecases;
import dev.rafex.insightbloom.moderation.domain.model.ModerationMessage;
import dev.rafex.insightbloom.moderation.domain.ports.ModerationMessageRepository;
public class RestoreMessageUseCase {
    private final ModerationMessageRepository repo;
    public RestoreMessageUseCase(ModerationMessageRepository repo) { this.repo = repo; }
    public void execute(String messageUuid, String updatedByUserUuid) {
        ModerationMessage msg = repo.findByUuid(messageUuid)
            .orElseThrow(() -> new IllegalArgumentException("message_not_found"));
        msg.restore(updatedByUserUuid);
        repo.save(msg);
    }
}
