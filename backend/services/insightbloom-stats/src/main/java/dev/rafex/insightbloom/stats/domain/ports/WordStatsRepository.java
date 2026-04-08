package dev.rafex.insightbloom.stats.domain.ports;
import dev.rafex.insightbloom.stats.domain.model.MessageType;
import dev.rafex.insightbloom.stats.domain.model.WordStats;
import java.util.List;
import java.util.Optional;
public interface WordStatsRepository {
    void save(WordStats stats);
    Optional<WordStats> findByConferenceAndWord(String conferenceUuid, String wordNormalized, MessageType type);
    List<WordStats> findByConference(String conferenceUuid);
}
