package dev.rafex.insightbloom.moderation.application.usecases;
import dev.rafex.insightbloom.moderation.domain.model.ModerationMessage;
import dev.rafex.insightbloom.moderation.domain.ports.ModerationMessageRepository;
import dev.rafex.insightbloom.moderation.domain.ports.QueryPort;
public class CensorMessageUseCase {
    private final ModerationMessageRepository repo;
    private final QueryPort queryPort;
    public CensorMessageUseCase(ModerationMessageRepository repo, QueryPort queryPort) {
        this.repo = repo; this.queryPort = queryPort;
    }
    public record Request(
        String messageUuid, String target, String reason, String updatedByUserUuid,
        String conferenceUuid, String wordText, String detailText
    ) {
        public Request(String messageUuid, String target, String reason, String updatedByUserUuid) {
            this(messageUuid, target, reason, updatedByUserUuid, null, null, null);
        }
    }
    public void execute(Request req) {
        ModerationMessage msg = repo.findByUuid(req.messageUuid())
            .or(() -> repo.findByMessageUuid(req.messageUuid()))
            .orElseGet(() -> {
                if (req.conferenceUuid() == null || req.conferenceUuid().isBlank())
                    throw new IllegalArgumentException("message_not_found");
                ModerationMessage created = new ModerationMessage(req.messageUuid(), req.conferenceUuid());
                repo.save(created);
                return created;
            });
        if ("word".equals(req.target())) {
            msg.censorWord(req.reason(), req.updatedByUserUuid());
        } else {
            msg.censorDetail(req.reason(), req.updatedByUserUuid());
        }
        repo.save(msg);
        queryPort.setMessageVisibility(req.messageUuid(), false);
    }
}
