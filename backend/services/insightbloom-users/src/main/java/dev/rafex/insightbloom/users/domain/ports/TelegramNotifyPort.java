package dev.rafex.insightbloom.users.domain.ports;

public interface TelegramNotifyPort {
    /** Best-effort notification; implementations must not throw on failure. */
    void notifyConference(String conferenceUuid, String message);
}
