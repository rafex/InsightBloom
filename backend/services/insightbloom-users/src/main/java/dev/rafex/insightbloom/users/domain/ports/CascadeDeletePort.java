package dev.rafex.insightbloom.users.domain.ports;

public interface CascadeDeletePort {
    void deleteConferenceData(String conferenceUuid);
}
