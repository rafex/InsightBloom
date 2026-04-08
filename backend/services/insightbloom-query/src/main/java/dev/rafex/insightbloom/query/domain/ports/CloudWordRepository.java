package dev.rafex.insightbloom.query.domain.ports;
import dev.rafex.insightbloom.query.domain.model.CloudWord;
import dev.rafex.insightbloom.query.domain.model.MessageType;
import java.util.List;
import java.util.Optional;
public interface CloudWordRepository {
    void save(CloudWord word);
    Optional<CloudWord> findByConferenceAndWord(String conferenceUuid, String wordNormalized, MessageType type);
    List<CloudWord> findVisibleByConferenceAndType(String conferenceUuid, MessageType type);
}
