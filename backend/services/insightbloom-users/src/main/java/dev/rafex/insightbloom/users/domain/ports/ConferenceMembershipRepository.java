package dev.rafex.insightbloom.users.domain.ports;

import dev.rafex.insightbloom.users.domain.model.ConferenceMembership;

import java.util.List;

public interface ConferenceMembershipRepository {
    void recordJoin(ConferenceMembership membership);

    boolean exists(String userUuid, String conferenceUuid);

    List<ConferenceMembership> findByUser(String userUuid);

    List<ConferenceMembership> findByConference(String conferenceUuid);

    void deleteByConference(String conferenceUuid);

    /** Usuarios con cuenta (no invitados anónimos) que se unieron a esta conferencia. */
    long countByConference(String conferenceUuid);

    /** Usuarios únicos (deduplicados) que se unieron a alguna de estas conferencias. */
    long countDistinctUsersByConferences(java.util.List<String> conferenceUuids);

    /** Igual que {@link #countDistinctUsersByConferences}, pero solo cuenta usuarios con status ACTIVE. */
    long countDistinctActiveUsersByConferences(java.util.List<String> conferenceUuids);
}
