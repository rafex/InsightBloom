package dev.rafex.insightbloom.moderation.application.usecases;
import dev.rafex.insightbloom.moderation.domain.model.ModerationWord;
import dev.rafex.insightbloom.moderation.domain.ports.ModerationWordRepository;
import dev.rafex.insightbloom.moderation.domain.ports.QueryPort;
public class CensorWordUseCase {
    private final ModerationWordRepository repo;
    private final QueryPort queryPort;
    public CensorWordUseCase(ModerationWordRepository repo, QueryPort queryPort) {
        this.repo = repo;
        this.queryPort = queryPort;
    }
    public record Request(String wordUuid, String reason, String updatedByUserUuid) {}
    public void execute(Request req) {
        ModerationWord word = repo.findByUuid(req.wordUuid())
            .orElseThrow(() -> new IllegalArgumentException("word_not_found"));
        word.censor(req.reason(), req.updatedByUserUuid());
        repo.save(word);
        try { queryPort.setWordVisibility(word.getConferenceUuid(), word.getWordNormalized(), false); }
        catch (Exception e) { /* best-effort */ }
    }
}
