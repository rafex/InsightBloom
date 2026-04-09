package dev.rafex.insightbloom.query.application.usecases;
import dev.rafex.insightbloom.query.domain.ports.CloudWordRepository;
public class SetVisibilityUseCase {
    private final CloudWordRepository repo;
    public SetVisibilityUseCase(CloudWordRepository repo) { this.repo = repo; }
    public record Request(String conferenceUuid, String wordNormalized, boolean visible) {}
    public void execute(Request req) {
        repo.updateVisibility(req.conferenceUuid(), req.wordNormalized(), req.visible());
    }
}
