package dev.rafex.insightbloom.ingest.domain.model;
import java.time.Instant;
import java.util.UUID;
public class Message {
    private final String uuid;
    private final String conferenceUuid;
    private final String authorUuid;
    private final AuthorKind authorKind;
    private final String deviceFingerprint;
    private final MessageType messageType;
    private final SourceType sourceType;
    private final Instant receivedAt;
    private final String wordOriginal;
    private String wordNormalized;
    private String wordCanonical;
    private WordIntent wordIntent;
    private final String detailOriginal;
    private String detailVisible;
    private DetailIntent detailIntent;
    private ContentStatus wordStatus;
    private ContentStatus detailStatus;
    private boolean isVisible;
    private final Instant createdAt;
    private Instant updatedAt;

    public Message(String conferenceUuid, String authorUuid, AuthorKind authorKind,
                   String deviceFingerprint, MessageType messageType, SourceType sourceType,
                   String wordOriginal, String detailOriginal, Instant receivedAt) {
        this.uuid = UUID.randomUUID().toString();
        this.conferenceUuid = conferenceUuid; this.authorUuid = authorUuid;
        this.authorKind = authorKind; this.deviceFingerprint = deviceFingerprint;
        this.messageType = messageType; this.sourceType = sourceType;
        this.wordOriginal = wordOriginal; this.detailOriginal = detailOriginal;
        this.receivedAt = receivedAt; this.createdAt = Instant.now(); this.updatedAt = this.createdAt;
        this.wordStatus = ContentStatus.VISIBLE; this.detailStatus = ContentStatus.VISIBLE;
        this.isVisible = true;
    }
    // Full constructor for DB mapping
    public Message(String uuid, String conferenceUuid, String authorUuid, AuthorKind authorKind,
                   String deviceFingerprint, MessageType messageType, SourceType sourceType,
                   Instant receivedAt, String wordOriginal, String wordNormalized, String wordCanonical,
                   WordIntent wordIntent, String detailOriginal, String detailVisible, DetailIntent detailIntent,
                   ContentStatus wordStatus, ContentStatus detailStatus, boolean isVisible,
                   Instant createdAt, Instant updatedAt) {
        this.uuid=uuid; this.conferenceUuid=conferenceUuid; this.authorUuid=authorUuid;
        this.authorKind=authorKind; this.deviceFingerprint=deviceFingerprint;
        this.messageType=messageType; this.sourceType=sourceType; this.receivedAt=receivedAt;
        this.wordOriginal=wordOriginal; this.wordNormalized=wordNormalized; this.wordCanonical=wordCanonical;
        this.wordIntent=wordIntent; this.detailOriginal=detailOriginal; this.detailVisible=detailVisible;
        this.detailIntent=detailIntent; this.wordStatus=wordStatus; this.detailStatus=detailStatus;
        this.isVisible=isVisible; this.createdAt=createdAt; this.updatedAt=updatedAt;
    }
    public String getUuid() { return uuid; }
    public String getConferenceUuid() { return conferenceUuid; }
    public String getAuthorUuid() { return authorUuid; }
    public AuthorKind getAuthorKind() { return authorKind; }
    public String getDeviceFingerprint() { return deviceFingerprint; }
    public MessageType getMessageType() { return messageType; }
    public SourceType getSourceType() { return sourceType; }
    public Instant getReceivedAt() { return receivedAt; }
    public String getWordOriginal() { return wordOriginal; }
    public String getWordNormalized() { return wordNormalized; }
    public String getWordCanonical() { return wordCanonical; }
    public WordIntent getWordIntent() { return wordIntent; }
    public String getDetailOriginal() { return detailOriginal; }
    public String getDetailVisible() { return detailVisible; }
    public DetailIntent getDetailIntent() { return detailIntent; }
    public ContentStatus getWordStatus() { return wordStatus; }
    public ContentStatus getDetailStatus() { return detailStatus; }
    public boolean isVisible() { return isVisible; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setWordNormalized(String v) { this.wordNormalized = v; }
    public void setWordCanonical(String v) { this.wordCanonical = v; }
    public void setWordIntent(WordIntent v) { this.wordIntent = v; }
    public void setDetailVisible(String v) { this.detailVisible = v; }
    public void setDetailIntent(DetailIntent v) { this.detailIntent = v; }
    public void setWordStatus(ContentStatus v) { this.wordStatus = v; }
    public void setDetailStatus(ContentStatus v) { this.detailStatus = v; }
    public void setVisible(boolean v) { this.isVisible = v; this.updatedAt = Instant.now(); }
}
