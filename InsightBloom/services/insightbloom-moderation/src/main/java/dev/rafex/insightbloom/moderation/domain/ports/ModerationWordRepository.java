package dev.rafex.insightbloom.moderation.domain.ports;
import dev.rafex.insightbloom.moderation.domain.model.ModerationWord;
import java.util.List;
import java.util.Optional;
public interface ModerationWordRepository {
    void save(ModerationWord word);
    Optional<ModerationWord> findByUuid(String uuid);
    Optional<ModerationWord> findByConferenceAndNormalized(String conferenceUuid, String wordNormalized);
    List<ModerationWord> findByConference(String conferenceUuid, int page, int pageSize);
    long countByConference(String conferenceUuid);
}
