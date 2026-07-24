package dev.rafex.insightbloom.survey.application.usecases;

import dev.rafex.insightbloom.survey.domain.model.AiMentorConfig;
import dev.rafex.insightbloom.survey.domain.ports.AiMentorConfigRepository;

import java.time.Instant;

public class AiMentorConfigUseCase {
    private static final int DEFAULT_RATE = 8;
    private final AiMentorConfigRepository repository;

    public AiMentorConfigUseCase(final AiMentorConfigRepository repository) {
        this.repository = repository;
    }

    public AiMentorConfig get(final String conferenceUuid) {
        return repository.findByConference(conferenceUuid).orElseGet(() ->
                new AiMentorConfig(conferenceUuid, false, null, null, true, DEFAULT_RATE, Instant.now()));
    }

    public AiMentorConfig save(final String conferenceUuid, final boolean enabled,
                               final String objective, final String prompt,
                               final boolean includePresentation, final int maxRequestsPerMinute) {
        if (conferenceUuid == null || conferenceUuid.isBlank()) throw new IllegalArgumentException("conference_required");
        if (objective != null && objective.length() > 2000) throw new IllegalArgumentException("objective_too_long");
        if (prompt != null && prompt.length() > 8000) throw new IllegalArgumentException("prompt_too_long");
        if (maxRequestsPerMinute < 1 || maxRequestsPerMinute > 30) {
            throw new IllegalArgumentException("max_requests_per_minute_out_of_range");
        }
        return repository.save(new AiMentorConfig(
                conferenceUuid, enabled, clean(objective), clean(prompt), includePresentation,
                maxRequestsPerMinute, Instant.now()));
    }

    private static String clean(final String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }
}
