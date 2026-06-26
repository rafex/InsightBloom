package dev.rafex.insightbloom.users.domain.ports;

public interface SurveyPort {
    /** Returns true if the given user already submitted the survey for this conference. */
    boolean hasResponded(String conferenceUuid, String token);
}
