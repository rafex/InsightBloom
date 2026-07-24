package dev.rafex.insightbloom.survey.domain.model;

import java.time.Instant;

/** Configuración pedagógica del tutor IA, aislada por evento. */
public record AiMentorConfig(
        String conferenceUuid,
        boolean enabled,
        String objective,
        String prompt,
        boolean includePresentation,
        int maxRequestsPerMinute,
        Instant updatedAt) {
}
