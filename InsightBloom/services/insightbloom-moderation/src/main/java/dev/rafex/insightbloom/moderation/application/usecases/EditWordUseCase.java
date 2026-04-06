package dev.rafex.insightbloom.moderation.application.usecases;

import dev.rafex.insightbloom.moderation.domain.model.ModerationWord;
import dev.rafex.insightbloom.moderation.domain.ports.ModerationWordRepository;

public class EditWordUseCase {
    private final ModerationWordRepository repo;

    public EditWordUseCase(ModerationWordRepository repo) { this.repo = repo; }

    public record Request(String wordUuid, String newValue, String updatedByUserUuid) {}

    public void execute(Request req) {
        ModerationWord word = repo.findByUuid(req.wordUuid())
            .orElseThrow(() -> new IllegalArgumentException("word_not_found"));
        word.edit(req.newValue(), req.updatedByUserUuid());
        repo.save(word);
    }
}
