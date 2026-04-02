package dev.rafex.insightbloom.moderation.application.usecases;
import dev.rafex.insightbloom.moderation.domain.model.ModerationWord;
import dev.rafex.insightbloom.moderation.domain.ports.ModerationWordRepository;
public class CensorWordUseCase {
    private final ModerationWordRepository repo;
    public CensorWordUseCase(ModerationWordRepository repo) { this.repo = repo; }
    public record Request(String wordUuid, String reason, String updatedByUserUuid) {}
    public void execute(Request req) {
        ModerationWord word = repo.findByUuid(req.wordUuid())
            .orElseThrow(() -> new IllegalArgumentException("word_not_found"));
        word.censor(req.reason(), req.updatedByUserUuid());
        repo.save(word);
    }
}
