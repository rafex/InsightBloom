package dev.rafex.insightbloom.moderation.application.usecases;

import dev.rafex.insightbloom.moderation.domain.ports.ModerationMessageRepository;

import java.time.Instant;
import java.util.Optional;

public class GetMessageAnswerUseCase {
    private final ModerationMessageRepository repo;

    public GetMessageAnswerUseCase(final ModerationMessageRepository repo) {
        this.repo = repo;
    }

    public record Result(String question, String answer, Instant answeredAt) {}

    public Optional<Result> execute(final String messageUuid) {
        return repo.findByUuid(messageUuid)
                .or(() -> repo.findByMessageUuid(messageUuid))
                .filter(m -> m.getAnswerText() != null && !m.getAnswerText().isBlank())
                .map(m -> new Result(
                        m.getEditedDetailValue() != null ? m.getEditedDetailValue() : m.getDetailText(),
                        m.getAnswerText(),
                        m.getAnsweredAt()));
    }
}
