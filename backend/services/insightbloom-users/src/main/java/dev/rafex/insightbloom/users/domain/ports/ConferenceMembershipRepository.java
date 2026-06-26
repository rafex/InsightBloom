package dev.rafex.insightbloom.users.domain.ports;

import dev.rafex.insightbloom.users.domain.model.ConferenceMembership;

import java.util.List;

public interface ConferenceMembershipRepository {
    void recordJoin(ConferenceMembership membership);

    boolean exists(String userUuid, String conferenceUuid);

    List<ConferenceMembership> findByUser(String userUuid);
}
