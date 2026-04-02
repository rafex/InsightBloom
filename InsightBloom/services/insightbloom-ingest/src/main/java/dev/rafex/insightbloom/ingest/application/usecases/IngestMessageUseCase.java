package dev.rafex.insightbloom.ingest.application.usecases;
import dev.rafex.insightbloom.ingest.domain.model.*;
import dev.rafex.insightbloom.ingest.domain.ports.*;
import dev.rafex.insightbloom.ingest.domain.services.*;
import java.time.Instant;
public class IngestMessageUseCase {
    private final MessageRepository messageRepo;
    private final ModerationPort moderationPort;
    private final StatsPort statsPort;
    private final QueryPort queryPort;
    private final WordNormalizationService normalizationService;
    private final IntentClassificationService intentService;

    public IngestMessageUseCase(MessageRepository messageRepo, ModerationPort moderationPort,
                                 StatsPort statsPort, QueryPort queryPort,
                                 WordNormalizationService normalizationService,
                                 IntentClassificationService intentService) {
        this.messageRepo=messageRepo; this.moderationPort=moderationPort;
        this.statsPort=statsPort; this.queryPort=queryPort;
        this.normalizationService=normalizationService; this.intentService=intentService;
    }

    public record IngestRequest(
        String conferenceUuid, String authorUuid, String authorKind, String displayName,
        String deviceFingerprint, String messageType, String sourceType,
        String wordOriginal, String detailOriginal, String receivedAt
    ) {}

    public Message execute(IngestRequest req) {
        Instant ts = req.receivedAt() != null ? Instant.parse(req.receivedAt()) : Instant.now();
        MessageType type = MessageType.valueOf(req.messageType().toUpperCase());
        SourceType source = SourceType.valueOf(req.sourceType().toUpperCase());
        AuthorKind authorKind;
        try { authorKind = AuthorKind.valueOf(req.authorKind().toUpperCase()); }
        catch (Exception e) { authorKind = AuthorKind.GUEST; }

        // 1. Normalize word
        String wordNorm = normalizationService.normalize(req.wordOriginal());
        String wordCanonical = normalizationService.canonical(wordNorm, wordNorm);

        // 2. Classify intent
        WordIntent wordIntent = intentService.classifyWord(req.wordOriginal(), req.detailOriginal());
        DetailIntent detailIntent = intentService.classifyDetail(req.detailOriginal());

        // 3. Evaluate censure
        ModerationPort.EvaluationResult eval;
        try {
            eval = moderationPort.evaluate(wordNorm, req.detailOriginal());
        } catch (Exception e) {
            // If moderation service unavailable, allow the message
            eval = new ModerationPort.EvaluationResult(false, false);
        }

        // 4. Build message
        Message msg = new Message(req.conferenceUuid(), req.authorUuid(), authorKind,
            req.deviceFingerprint(), type, source, req.wordOriginal(), req.detailOriginal(), ts);
        msg.setWordNormalized(wordNorm);
        msg.setWordCanonical(wordCanonical);
        msg.setWordIntent(wordIntent);
        msg.setDetailIntent(detailIntent);

        // 5. Apply censure results
        boolean wordVisible = true;
        if (eval.wordBlocked()) {
            msg.setWordStatus(ContentStatus.CENSURADO_AUTO);
            msg.setDetailStatus(ContentStatus.CENSURADO_AUTO);
            msg.setVisible(false);
            wordVisible = false;
        } else if (eval.detailBlocked()) {
            msg.setDetailStatus(ContentStatus.CENSURADO_AUTO);
            msg.setDetailVisible("detalle censurado");
        } else {
            msg.setDetailVisible(req.detailOriginal());
        }

        // 6. Persist
        messageRepo.save(msg);

        // 7. Notify stats (fire and forget in PoC)
        try {
            statsPort.recalc(new StatsPort.RecalcRequest(
                req.conferenceUuid(), wordNorm, wordCanonical,
                type.name(), wordIntent.name(), wordVisible
            ));
        } catch (Exception e) { /* log */ }

        // 8. Notify query
        try {
            queryPort.update(new QueryPort.UpdateRequest(
                req.conferenceUuid(), wordNorm, wordCanonical, type.name(),
                0.0, 0L, msg.getUuid(),
                req.displayName() != null ? req.displayName() : req.authorUuid(),
                authorKind.name(), msg.getDetailVisible(), ts.toString(), wordVisible
            ));
        } catch (Exception e) { /* log */ }

        return msg;
    }
}
