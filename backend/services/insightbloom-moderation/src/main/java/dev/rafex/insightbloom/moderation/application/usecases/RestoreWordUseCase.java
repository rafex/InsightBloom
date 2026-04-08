package dev.rafex.insightbloom.moderation.application.usecases;
import dev.rafex.insightbloom.moderation.domain.model.ModerationWord;
import dev.rafex.insightbloom.moderation.domain.ports.ModerationWordRepository;
public class RestoreWordUseCase {
    private final ModerationWordRepository repo;
    public RestoreWordUseCase(ModerationWordRepository repo) { this.repo = repo; }
    public void execute(String wordUuid, String updatedByUserUuid) {
        ModerationWord word = repo.findByUuid(wordUuid)
            .orElseThrow(() -> new IllegalArgumentException("word_not_found"));
        word.restore(updatedByUserUuid);
        repo.save(word);
    }
}
