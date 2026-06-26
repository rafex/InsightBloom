package dev.rafex.insightbloom.users.domain.model;

import java.time.Instant;
import java.util.UUID;

public class ConferenceMembership {
    private final String uuid;
    private final String userUuid;
    private final String conferenceUuid;
    private final String conferenceNameSnapshot;
    private final String conferenceFriendlyIdSnapshot;
    private Instant joinedAt;

    public ConferenceMembership(final String userUuid, final String conferenceUuid,
                                 final String conferenceNameSnapshot, final String conferenceFriendlyIdSnapshot) {
        this.uuid = UUID.randomUUID().toString();
        this.userUuid = userUuid;
        this.conferenceUuid = conferenceUuid;
        this.conferenceNameSnapshot = conferenceNameSnapshot;
        this.conferenceFriendlyIdSnapshot = conferenceFriendlyIdSnapshot;
        this.joinedAt = Instant.now();
    }

    public ConferenceMembership(final String uuid, final String userUuid, final String conferenceUuid,
                                 final String conferenceNameSnapshot, final String conferenceFriendlyIdSnapshot,
                                 final Instant joinedAt) {
        this.uuid = uuid;
        this.userUuid = userUuid;
        this.conferenceUuid = conferenceUuid;
        this.conferenceNameSnapshot = conferenceNameSnapshot;
        this.conferenceFriendlyIdSnapshot = conferenceFriendlyIdSnapshot;
        this.joinedAt = joinedAt;
    }

    public String getUuid() { return uuid; }
    public String getUserUuid() { return userUuid; }
    public String getConferenceUuid() { return conferenceUuid; }
    public String getConferenceNameSnapshot() { return conferenceNameSnapshot; }
    public String getConferenceFriendlyIdSnapshot() { return conferenceFriendlyIdSnapshot; }
    public Instant getJoinedAt() { return joinedAt; }
}
