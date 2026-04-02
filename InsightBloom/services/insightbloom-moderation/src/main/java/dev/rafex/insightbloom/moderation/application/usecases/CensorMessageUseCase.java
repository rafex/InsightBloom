package dev.rafex.insightbloom.moderation.application.usecases;
import dev.rafex.insightbloom.moderation.domain.model.ModerationMessage;
import dev.rafex.insightbloom.moderation.domain.ports.ModerationMessageRepository;
public class CensorMessageUseCase {
    private final ModerationMessageRepository repo;
    public CensorMessageUseCase(ModerationMessageRepository repo) { this.repo = repo; }
    public record Request(String messageUuid, String target, String reason, String updatedByUserUuid) {}
    public void execute(Request req) {
        ModerationMessage msg = repo.findByUuid(req.messageUuid())
            .orElseThrow(() -> new IllegalArgumentException("message_not_found"));
        if ("word".equals(req.target())) {
            msg.censorWord(req.reason(), req.updatedByUserUuid());
        } else {
            msg.censorDetail(req.reason(), req.updatedByUserUuid());
        }
        repo.save(msg);
    }
}
