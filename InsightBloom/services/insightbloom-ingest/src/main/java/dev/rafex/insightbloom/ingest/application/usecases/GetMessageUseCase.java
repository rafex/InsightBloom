package dev.rafex.insightbloom.ingest.application.usecases;
import dev.rafex.insightbloom.ingest.domain.model.Message;
import dev.rafex.insightbloom.ingest.domain.ports.MessageRepository;
import java.util.Optional;
public class GetMessageUseCase {
    private final MessageRepository repo;
    public GetMessageUseCase(MessageRepository repo) { this.repo = repo; }
    public Optional<Message> execute(String uuid) { return repo.findByUuid(uuid); }
}
