package dev.rafex.insightbloom.ingest.domain.ports;
import dev.rafex.insightbloom.ingest.domain.model.Message;
import java.util.Optional;
public interface MessageRepository {
    void save(Message message);
    Optional<Message> findByUuid(String uuid);
}
