package dev.rafex.insightbloom.moderation.application.usecases;
import dev.rafex.insightbloom.moderation.domain.model.ContentStatus;
import dev.rafex.insightbloom.moderation.domain.model.ModerationMessage;
import dev.rafex.insightbloom.moderation.domain.model.ModerationWord;
import dev.rafex.insightbloom.moderation.domain.ports.ModerationMessageRepository;
import dev.rafex.insightbloom.moderation.domain.ports.ModerationWordRepository;
import dev.rafex.insightbloom.moderation.domain.services.AutoCensureService;
public class EvaluateCensureUseCase {
    private final AutoCensureService autoCensureService;
    private final ModerationWordRepository wordRepo;
    private final ModerationMessageRepository messageRepo;
    public EvaluateCensureUseCase(AutoCensureService autoCensureService, ModerationWordRepository wordRepo, ModerationMessageRepository messageRepo) {
        this.autoCensureService = autoCensureService;
        this.wordRepo = wordRepo;
        this.messageRepo = messageRepo;
    }
    public record EvaluateRequest(String word, String detail, String conferenceUuid, String wordCanonical, String messageUuid, String wordText, String detailText) {}
    public record EvaluateResult(boolean wordBlocked, boolean detailBlocked) {}
    public EvaluateResult execute(EvaluateRequest req) {
        var result = autoCensureService.evaluate(req.word(), req.detail());
        // Register word in moderation_words (upsert by conference + normalized word)
        if (req.conferenceUuid() != null && req.word() != null && !req.word().isBlank()) {
            String canonical = req.wordCanonical() != null ? req.wordCanonical() : req.word();
            var existing = wordRepo.findByConferenceAndNormalized(req.conferenceUuid(), req.word());
            if (existing.isEmpty()) {
                ContentStatus status = result.wordBlocked() ? ContentStatus.CENSURADO_AUTO : ContentStatus.VISIBLE;
                ModerationWord mw = new ModerationWord(req.conferenceUuid(), req.word(), canonical, status);
                wordRepo.save(mw);
            }
        }
        // Register message in moderation_messages
        if (req.messageUuid() != null && !req.messageUuid().isBlank()) {
            var existingMsg = messageRepo.findByMessageUuid(req.messageUuid());
            if (existingMsg.isEmpty()) {
                ModerationMessage mm = new ModerationMessage(req.messageUuid(), req.conferenceUuid());
                mm.initContent(req.wordText(), req.detailText());
                if (result.wordBlocked()) mm.censorWord(null, "auto");
                if (result.detailBlocked()) mm.censorDetail(null, "auto");
                messageRepo.save(mm);
            }
        }
        return new EvaluateResult(result.wordBlocked(), result.detailBlocked());
    }
}
