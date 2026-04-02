package dev.rafex.insightbloom.query.domain.model;
import java.time.Instant;
import java.util.UUID;
public class CloudWord {
    private final String uuid;
    private final String conferenceUuid;
    private final MessageType messageType;
    private final String wordNormalized;
    private String wordCanonical;
    private double relevanceScore;
    private long messageCount;
    private Instant firstSeenAt;
    private Instant lastSeenAt;
    private boolean visible;
    private Instant updatedAt;

    public CloudWord(String conferenceUuid, MessageType messageType, String wordNormalized, String wordCanonical) {
        this.uuid = UUID.randomUUID().toString();
        this.conferenceUuid = conferenceUuid;
        this.messageType = messageType;
        this.wordNormalized = wordNormalized;
        this.wordCanonical = wordCanonical;
        this.relevanceScore = 0.0;
        this.messageCount = 0;
        this.firstSeenAt = Instant.now();
        this.lastSeenAt = this.firstSeenAt;
        this.visible = true;
        this.updatedAt = this.firstSeenAt;
    }
    public CloudWord(String uuid, String conferenceUuid, MessageType messageType, String wordNormalized,
                     String wordCanonical, double relevanceScore, long messageCount,
                     Instant firstSeenAt, Instant lastSeenAt, boolean visible, Instant updatedAt) {
        this.uuid=uuid; this.conferenceUuid=conferenceUuid; this.messageType=messageType;
        this.wordNormalized=wordNormalized; this.wordCanonical=wordCanonical;
        this.relevanceScore=relevanceScore; this.messageCount=messageCount;
        this.firstSeenAt=firstSeenAt; this.lastSeenAt=lastSeenAt; this.visible=visible; this.updatedAt=updatedAt;
    }
    public String getUuid() { return uuid; }
    public String getConferenceUuid() { return conferenceUuid; }
    public MessageType getMessageType() { return messageType; }
    public String getWordNormalized() { return wordNormalized; }
    public String getWordCanonical() { return wordCanonical; }
    public double getRelevanceScore() { return relevanceScore; }
    public long getMessageCount() { return messageCount; }
    public Instant getFirstSeenAt() { return firstSeenAt; }
    public Instant getLastSeenAt() { return lastSeenAt; }
    public boolean isVisible() { return visible; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setWordCanonical(String v) { this.wordCanonical = v; }
    public void setRelevanceScore(double v) { this.relevanceScore = v; }
    public void setMessageCount(long v) { this.messageCount = v; }
    public void setLastSeenAt(Instant v) { this.lastSeenAt = v; }
    public void setVisible(boolean v) { this.visible = v; this.updatedAt = Instant.now(); }
    public void setUpdatedAt(Instant v) { this.updatedAt = v; }
}
