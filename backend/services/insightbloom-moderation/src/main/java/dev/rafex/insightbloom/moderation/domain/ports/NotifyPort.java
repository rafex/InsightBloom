package dev.rafex.insightbloom.moderation.domain.ports;

public interface NotifyPort {
    /** Best-effort notification; implementations must not throw on failure. */
    void notifyDoubtAnswered(String authorUuid, String conferenceUuid, String question, String answer);
}
