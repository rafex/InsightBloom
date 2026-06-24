package dev.rafex.insightbloom.moderation.application.usecases;

import dev.rafex.insightbloom.moderation.domain.model.ModerationWord;
import dev.rafex.insightbloom.moderation.domain.ports.ModerationWordRepository;

public class DeleteWordUseCase {
    private final ModerationWordRepository repo;

    public DeleteWordUseCase(ModerationWordRepository repo) { this.repo = repo; }

    public record Request(String wordUuid, String deletedByUserUuid) {}

    public void execute(Request req) {
        ModerationWord word = repo.findByUuid(req.wordUuid())
            .orElseThrow(() -> new IllegalArgumentException("word_not_found"));
        word.delete(req.deletedByUserUuid());
        repo.save(word);
    }
}
