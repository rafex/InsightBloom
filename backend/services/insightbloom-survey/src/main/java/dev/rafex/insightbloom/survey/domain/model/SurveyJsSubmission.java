package dev.rafex.insightbloom.survey.domain.model;

import java.time.Instant;

public record SurveyJsSubmission(String uuid, String conferenceUuid, String definitionUuid,
                                 int definitionVersion, String userUuid, String payloadJson,
                                 Instant submittedAt) {}
