package dev.rafex.insightbloom.query.application.usecases;
import dev.rafex.insightbloom.query.domain.model.*;
import dev.rafex.insightbloom.query.domain.ports.*;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
public class UpdateCloudUseCase {
    private final CloudWordRepository cloudRepo;
    private final WordTimelineRepository timelineRepo;
    private final CloudEventBus eventBus;
    public UpdateCloudUseCase(CloudWordRepository cloudRepo, WordTimelineRepository timelineRepo) {
        this(cloudRepo, timelineRepo, null);
    }
    public UpdateCloudUseCase(CloudWordRepository cloudRepo, WordTimelineRepository timelineRepo,
                               CloudEventBus eventBus) {
        this.cloudRepo = cloudRepo; this.timelineRepo = timelineRepo; this.eventBus = eventBus;
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
        // El ingest no lleva la cuenta histórica (cada mensaje se evalúa de forma aislada),
        // así que se acumula aquí: cada llamada a update() representa una nueva mención.
        cloud.setWordCanonical(req.wordCanonical());
        cloud.setMessageCount(cloud.getMessageCount() + 1);
        cloud.setRelevanceScore(cloud.getRelevanceScore() + 1);
        cloud.setLastSeenAt(Instant.parse(req.receivedAt()));
        cloud.setVisible(req.wordVisible());
        cloudRepo.save(cloud);
        publishUpdate(cloud);
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

    /** Best-effort — a NATS hiccup must not break the actual cloud update. */
    private void publishUpdate(final CloudWord cloud) {
        if (eventBus == null) return;
        try {
            final Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("wordNormalized", cloud.getWordNormalized());
            payload.put("wordCanonical", cloud.getWordCanonical());
            payload.put("relevanceScore", cloud.getRelevanceScore());
            payload.put("messageCount", cloud.getMessageCount());
            payload.put("visible", cloud.isVisible());
            eventBus.publish(cloud.getConferenceUuid(), cloud.getMessageType(), payload);
        } catch (final Exception ignored) {
            // logged at the transport level (NatsCloudEventBus)
        }
    }
}
