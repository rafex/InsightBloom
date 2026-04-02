package dev.rafex.insightbloom.query.application.usecases;
import dev.rafex.insightbloom.query.domain.model.*;
import dev.rafex.insightbloom.query.domain.ports.*;
import java.time.Instant;
public class UpdateCloudUseCase {
    private final CloudWordRepository cloudRepo;
    private final WordTimelineRepository timelineRepo;
    public UpdateCloudUseCase(CloudWordRepository cloudRepo, WordTimelineRepository timelineRepo) {
        this.cloudRepo = cloudRepo; this.timelineRepo = timelineRepo;
    }
    public record UpdateRequest(
        String conferenceUuid, String wordNormalized, String wordCanonical,
        String messageType, double relevanceScore, long messageCount,
        String messageUuid, String authorLabel, String authorKind,
        String detailVisible, String receivedAt, boolean wordVisible
    ) {}
    public void execute(UpdateRequest req) {
        MessageType type = MessageType.valueOf(req.messageType().toUpperCase());
        AuthorKind authorKind;
        try { authorKind = AuthorKind.valueOf(req.authorKind().toUpperCase()); }
        catch (Exception e) { authorKind = AuthorKind.GUEST; }
        // Update cloud word
        CloudWord cloud = cloudRepo.findByConferenceAndWord(req.conferenceUuid(), req.wordNormalized(), type)
            .orElse(new CloudWord(req.conferenceUuid(), type, req.wordNormalized(), req.wordCanonical()));
        cloud.setWordCanonical(req.wordCanonical());
        cloud.setRelevanceScore(req.relevanceScore());
        cloud.setMessageCount(req.messageCount());
        cloud.setLastSeenAt(Instant.parse(req.receivedAt()));
        cloud.setVisible(req.wordVisible());
        cloudRepo.save(cloud);
        // Add timeline entry if not exists
        if (req.messageUuid() != null && timelineRepo.findByMessageUuid(req.messageUuid()).isEmpty()) {
            WordTimeline entry = new WordTimeline(
                req.conferenceUuid(), req.wordNormalized(), req.messageUuid(),
                type, req.authorLabel(), authorKind, req.detailVisible(),
                Instant.parse(req.receivedAt())
            );
            timelineRepo.save(entry);
        }
    }
}
