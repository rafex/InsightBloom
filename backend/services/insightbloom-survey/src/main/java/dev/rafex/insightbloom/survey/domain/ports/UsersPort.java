package dev.rafex.insightbloom.survey.domain.ports;

import java.util.Optional;

public interface UsersPort {
    record ValidationResult(boolean valid, String subjectUuid, String kind, String role) {}

    ValidationResult validate(String token);

    /** Consulta la política central de acceso del evento para el usuario autenticado. */
    boolean hasConferenceAccess(String conferenceUuid, String token);

    /** Resolves a user's display name for organizer-facing results. Empty if unknown/unreachable. */
    Optional<String> getDisplayName(String userUuid);

    /** True si userUuid es el creador (organizador dueño) de conferenceUuid. Usado para exigir
     *  ownership real, no solo "tiene algún rol organizer", en las mutaciones de encuesta. */
    boolean isConferenceOwner(String conferenceUuid, String userUuid);
}
