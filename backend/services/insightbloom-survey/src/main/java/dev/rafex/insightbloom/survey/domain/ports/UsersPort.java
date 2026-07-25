package dev.rafex.insightbloom.survey.domain.ports;

import java.util.List;
import java.util.Optional;

public interface UsersPort {
    record ValidationResult(boolean valid, String subjectUuid, String kind, String role) {}
    record AttendeeSummary(String uuid, String displayName, String email, String joinedAt) {}
    /** Metadatos no sensibles del evento, para dar contexto a los prompts de IA. */
    record ConferenceSummary(String name, String eventTypeKey, String eventDate,
                             String startTime, String endTime, String venue) {}

    ValidationResult validate(String token);

    /** Consulta la política central de acceso del evento para el usuario autenticado. */
    boolean hasConferenceAccess(String conferenceUuid, String token);

    /** Resolves a user's display name for organizer-facing results. Empty if unknown/unreachable. */
    Optional<String> getDisplayName(String userUuid);

    /** True si userUuid es el creador (organizador dueño) de conferenceUuid. Usado para exigir
     *  ownership real, no solo "tiene algún rol organizer", en las mutaciones de encuesta. */
    boolean isConferenceOwner(String conferenceUuid, String userUuid);

    /** True si el token tiene MANAGE_SURVEY para esta conferencia. */
    boolean hasSurveyManagementAccess(String conferenceUuid, String token);

    List<AttendeeSummary> listConferenceAttendees(String conferenceUuid, String token);

    /** Metadatos del evento para dar contexto a los prompts de IA (nombre, tipo, fecha, lugar). */
    Optional<ConferenceSummary> getConferenceSummary(String conferenceUuid);
}
