package dev.rafex.insightbloom.moderation.domain.ports;
import dev.rafex.insightbloom.moderation.domain.model.ModerationMessage;
import java.util.List;
import java.util.Optional;
public interface ModerationMessageRepository {
    void save(ModerationMessage msg);
    Optional<ModerationMessage> findByUuid(String uuid);
    Optional<ModerationMessage> findByMessageUuid(String messageUuid);
    List<ModerationMessage> findByConference(String conferenceUuid, int page, int pageSize);
    List<ModerationMessage> findByConferenceAndStatus(String conferenceUuid, String status, int page, int pageSize);
    long countByConference(String conferenceUuid);
    long countByConferenceAndStatus(String conferenceUuid, String status);
}
