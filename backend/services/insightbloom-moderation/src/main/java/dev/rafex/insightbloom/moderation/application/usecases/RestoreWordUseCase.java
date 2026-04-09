package dev.rafex.insightbloom.moderation.application.usecases;
import dev.rafex.insightbloom.moderation.domain.model.ModerationWord;
import dev.rafex.insightbloom.moderation.domain.ports.ModerationWordRepository;
import dev.rafex.insightbloom.moderation.domain.ports.QueryPort;
public class RestoreWordUseCase {
    private final ModerationWordRepository repo;
    private final QueryPort queryPort;
    public RestoreWordUseCase(ModerationWordRepository repo, QueryPort queryPort) {
        this.repo = repo;
        this.queryPort = queryPort;
    }
    public void execute(String wordUuid, String updatedByUserUuid) {
        ModerationWord word = repo.findByUuid(wordUuid)
            .orElseThrow(() -> new IllegalArgumentException("word_not_found"));
        word.restore(updatedByUserUuid);
        repo.save(word);
        try { queryPort.setWordVisibility(word.getConferenceUuid(), word.getWordNormalized(), true); }
        catch (Exception e) { /* best-effort */ }
    }
}
