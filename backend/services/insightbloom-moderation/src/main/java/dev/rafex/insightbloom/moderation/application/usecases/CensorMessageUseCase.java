package dev.rafex.insightbloom.moderation.application.usecases;
import dev.rafex.insightbloom.moderation.domain.model.ModerationMessage;
import dev.rafex.insightbloom.moderation.domain.ports.ModerationMessageRepository;
import dev.rafex.insightbloom.moderation.domain.ports.QueryPort;
public class CensorMessageUseCase {
    private final ModerationMessageRepository repo;
    private final QueryPort queryPort;
    public CensorMessageUseCase(ModerationMessageRepository repo, QueryPort queryPort) {
        this.repo = repo;
        this.queryPort = queryPort;
    }

    public record Request(
        String messageUuid,
        String target,
        String reason,
        String updatedByUserUuid,
        String conferenceUuid,
        String wordText,
        String detailText
    ) {
        /** Convenience constructor for callers that already have a moderation_messages entry. */
        public Request(String messageUuid, String target, String reason, String updatedByUserUuid) {
            this(messageUuid, target, reason, updatedByUserUuid, null, null, null);
        }
    }

    public void execute(Request req) {
        // 1. Try by moderation_messages.uuid (primary key).
        // 2. Fallback to message_uuid (original ingest UUID) for messages that predate
        //    the moderation-registration fix or that arrive directly from the frontend.
        // 3. If still not found and conferenceUuid is provided, create the entry on-demand
        //    so that messages can be moderated without re-sending them through ingest.
        ModerationMessage msg = repo.findByUuid(req.messageUuid())
            .or(() -> repo.findByMessageUuid(req.messageUuid()))
            .orElseGet(() -> {
                if (req.conferenceUuid() == null || req.conferenceUuid().isBlank()) {
                    throw new IllegalArgumentException("message_not_found");
                }
                ModerationMessage created = new ModerationMessage(req.messageUuid(), req.conferenceUuid());
                created.initContent(req.wordText(), req.detailText());
                repo.save(created);
                return created;
            });

        if ("word".equals(req.target())) {
            msg.censorWord(req.reason(), req.updatedByUserUuid());
        } else {
            msg.censorDetail(req.reason(), req.updatedByUserUuid());
        }
        repo.save(msg);
        try { queryPort.setMessageVisibility(msg.getMessageUuid(), false); } catch (Exception ignored) {}
    }
}
