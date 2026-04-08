package dev.rafex.insightbloom.query.domain.ports;
import dev.rafex.insightbloom.query.domain.model.WordTimeline;
import java.util.List;
import java.util.Optional;
public interface WordTimelineRepository {
    void save(WordTimeline entry);
    Optional<WordTimeline> findByMessageUuid(String messageUuid);
    List<WordTimeline> findVisibleByConferenceAndWord(String conferenceUuid, String wordNormalized);
}
