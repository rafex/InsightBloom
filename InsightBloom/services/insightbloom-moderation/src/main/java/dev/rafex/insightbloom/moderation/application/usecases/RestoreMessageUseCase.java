package dev.rafex.insightbloom.moderation.application.usecases;
import dev.rafex.insightbloom.moderation.domain.model.ModerationMessage;
import dev.rafex.insightbloom.moderation.domain.ports.ModerationMessageRepository;
import dev.rafex.insightbloom.moderation.domain.ports.QueryPort;
public class RestoreMessageUseCase {
    private final ModerationMessageRepository repo;
    private final QueryPort queryPort;
    public RestoreMessageUseCase(ModerationMessageRepository repo, QueryPort queryPort) {
        this.repo = repo; this.queryPort = queryPort;
    }
    public void execute(String messageUuid, String updatedByUserUuid) {
        ModerationMessage msg = repo.findByUuid(messageUuid)
            .or(() -> repo.findByMessageUuid(messageUuid))
            .orElseThrow(() -> new IllegalArgumentException("message_not_found"));
        msg.restore(updatedByUserUuid);
        repo.save(msg);
        queryPort.setMessageVisibility(messageUuid, true);
    }
}
