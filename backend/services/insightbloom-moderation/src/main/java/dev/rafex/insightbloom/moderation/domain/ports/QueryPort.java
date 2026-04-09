package dev.rafex.insightbloom.moderation.domain.ports;
public interface QueryPort {
    void setWordVisibility(String conferenceUuid, String wordNormalized, boolean visible);
    void setMessageVisibility(String messageUuid, boolean visible);
}
