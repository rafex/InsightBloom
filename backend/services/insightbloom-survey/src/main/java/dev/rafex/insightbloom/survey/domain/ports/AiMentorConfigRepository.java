package dev.rafex.insightbloom.survey.domain.ports;

import dev.rafex.insightbloom.survey.domain.model.AiMentorConfig;

import java.util.Optional;

public interface AiMentorConfigRepository {
    Optional<AiMentorConfig> findByConference(String conferenceUuid);

    AiMentorConfig save(AiMentorConfig config);

    void deleteByConference(String conferenceUuid);
}
