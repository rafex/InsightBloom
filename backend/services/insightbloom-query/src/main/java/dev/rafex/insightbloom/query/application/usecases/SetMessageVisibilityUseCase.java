package dev.rafex.insightbloom.query.application.usecases;
import dev.rafex.insightbloom.query.domain.ports.WordTimelineRepository;
public class SetMessageVisibilityUseCase {
    private final WordTimelineRepository repo;
    public SetMessageVisibilityUseCase(WordTimelineRepository repo) { this.repo = repo; }
    public record Request(String messageUuid, boolean visible) {}
    public void execute(Request req) {
        repo.updateMessageVisibility(req.messageUuid(), req.visible());
    }
}
