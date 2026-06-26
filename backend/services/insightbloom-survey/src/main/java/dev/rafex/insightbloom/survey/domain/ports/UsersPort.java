package dev.rafex.insightbloom.survey.domain.ports;

import java.util.Optional;

public interface UsersPort {
    record ValidationResult(boolean valid, String subjectUuid, String kind, String role) {}

    ValidationResult validate(String token);

    /** Resolves a user's display name for organizer-facing results. Empty if unknown/unreachable. */
    Optional<String> getDisplayName(String userUuid);
}
