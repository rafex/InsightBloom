package dev.rafex.insightbloom.survey.domain.ports;

import java.util.List;

/** Persistencia del candado de acceso a una encuesta por conferencia. */
public interface SurveyAccessRepository {
    boolean isReleased(String conferenceUuid, String userUuid);

    boolean isReleasedForAll(String conferenceUuid);

    void releaseForAll(String conferenceUuid);

    void releaseUsers(String conferenceUuid, List<String> userUuids);

    void deleteByConference(String conferenceUuid);
}
