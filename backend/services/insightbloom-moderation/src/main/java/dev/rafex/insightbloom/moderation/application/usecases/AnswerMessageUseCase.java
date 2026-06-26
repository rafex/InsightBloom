package dev.rafex.insightbloom.moderation.application.usecases;

import dev.rafex.insightbloom.moderation.domain.model.ModerationMessage;
import dev.rafex.insightbloom.moderation.domain.ports.ModerationMessageRepository;
import dev.rafex.insightbloom.moderation.domain.ports.NotifyPort;

public class AnswerMessageUseCase {
    private final ModerationMessageRepository repo;
    private final NotifyPort notifyPort;

    public AnswerMessageUseCase(final ModerationMessageRepository repo, final NotifyPort notifyPort) {
        this.repo = repo;
        this.notifyPort = notifyPort;
    }

    public record Request(String messageUuid, String answerText, String answeredByUserUuid) {}

    public void execute(final Request req) {
        final ModerationMessage msg = repo.findByUuid(req.messageUuid())
                .or(() -> repo.findByMessageUuid(req.messageUuid()))
                .orElseThrow(() -> new IllegalArgumentException("message_not_found"));

        msg.answer(req.answerText(), req.answeredByUserUuid());
        repo.save(msg);

        if (msg.getAuthorUuid() != null && !msg.getAuthorUuid().isBlank()) {
            final String question = msg.getEditedDetailValue() != null ? msg.getEditedDetailValue() : msg.getDetailText();
            try {
                notifyPort.notifyDoubtAnswered(msg.getAuthorUuid(), msg.getConferenceUuid(), question, req.answerText());
            } catch (final Exception ignored) {
                // best-effort: the answer is saved regardless of notification outcome
            }
        }
    }
}
