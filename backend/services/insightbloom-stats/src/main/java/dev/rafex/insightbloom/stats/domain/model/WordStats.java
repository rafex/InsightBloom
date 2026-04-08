package dev.rafex.insightbloom.stats.domain.model;
import java.time.Instant;
import java.util.UUID;
public class WordStats {
    private final String uuid;
    private final String conferenceUuid;
    private final MessageType messageType;
    private final String wordNormalized;
    private String wordCanonical;
    private long countTotal;
    private long countVisible;
    private long countCensored;
    private double scoreIntent; // avg intent weight
    private double relevanceScore;
    private Instant updatedAt;

    public WordStats(String conferenceUuid, MessageType messageType, String wordNormalized, String wordCanonical) {
        this.uuid = UUID.randomUUID().toString();
        this.conferenceUuid = conferenceUuid;
        this.messageType = messageType;
        this.wordNormalized = wordNormalized;
        this.wordCanonical = wordCanonical;
        this.countTotal = 0; this.countVisible = 0; this.countCensored = 0;
        this.scoreIntent = 1.0; this.relevanceScore = 0.0;
        this.updatedAt = Instant.now();
    }
    public WordStats(String uuid, String conferenceUuid, MessageType messageType, String wordNormalized,
                     String wordCanonical, long countTotal, long countVisible, long countCensored,
                     double scoreIntent, double relevanceScore, Instant updatedAt) {
        this.uuid = uuid; this.conferenceUuid = conferenceUuid; this.messageType = messageType;
        this.wordNormalized = wordNormalized; this.wordCanonical = wordCanonical;
        this.countTotal = countTotal; this.countVisible = countVisible; this.countCensored = countCensored;
        this.scoreIntent = scoreIntent; this.relevanceScore = relevanceScore; this.updatedAt = updatedAt;
    }

    public void recalculate() {
        double typeWeight = messageType == MessageType.DOUBT ? 1.2 : 1.0;
        this.relevanceScore = countVisible * typeWeight * scoreIntent;
        this.updatedAt = Instant.now();
    }

    public String getUuid() { return uuid; }
    public String getConferenceUuid() { return conferenceUuid; }
    public MessageType getMessageType() { return messageType; }
    public String getWordNormalized() { return wordNormalized; }
    public String getWordCanonical() { return wordCanonical; }
    public long getCountTotal() { return countTotal; }
    public long getCountVisible() { return countVisible; }
    public long getCountCensored() { return countCensored; }
    public double getScoreIntent() { return scoreIntent; }
    public double getRelevanceScore() { return relevanceScore; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setCountTotal(long v) { this.countTotal = v; }
    public void setCountVisible(long v) { this.countVisible = v; }
    public void setCountCensored(long v) { this.countCensored = v; }
    public void setScoreIntent(double v) { this.scoreIntent = v; }
    public void setWordCanonical(String v) { this.wordCanonical = v; }
}
