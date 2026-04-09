package dev.rafex.insightbloom.moderation.domain.ports;
public interface QueryPort {
    void setMessageVisibility(String messageUuid, boolean visible);
}
