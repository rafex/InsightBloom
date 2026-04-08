package dev.rafex.insightbloom.query.domain.model;
import java.time.Instant;
import java.util.UUID;
public class WordTimeline {
    private final String uuid;
    private final String conferenceUuid;
    private final String wordNormalized;
    private final String messageUuid;
    private final MessageType messageType;
    private final String authorLabel;
    private final AuthorKind authorKind;
    private String detailVisible;
    private final Instant receivedAt;
    private boolean visible;
    private Instant updatedAt;

    public WordTimeline(String conferenceUuid, String wordNormalized, String messageUuid,
                        MessageType messageType, String authorLabel, AuthorKind authorKind,
                        String detailVisible, Instant receivedAt) {
        this.uuid = UUID.randomUUID().toString();
        this.conferenceUuid = conferenceUuid; this.wordNormalized = wordNormalized;
        this.messageUuid = messageUuid; this.messageType = messageType;
        this.authorLabel = authorLabel; this.authorKind = authorKind;
        this.detailVisible = detailVisible; this.receivedAt = receivedAt;
        this.visible = true; this.updatedAt = Instant.now();
    }
    public WordTimeline(String uuid, String conferenceUuid, String wordNormalized, String messageUuid,
                        MessageType messageType, String authorLabel, AuthorKind authorKind,
                        String detailVisible, Instant receivedAt, boolean visible, Instant updatedAt) {
        this.uuid=uuid; this.conferenceUuid=conferenceUuid; this.wordNormalized=wordNormalized;
        this.messageUuid=messageUuid; this.messageType=messageType; this.authorLabel=authorLabel;
        this.authorKind=authorKind; this.detailVisible=detailVisible; this.receivedAt=receivedAt;
        this.visible=visible; this.updatedAt=updatedAt;
    }
    public String getUuid() { return uuid; }
    public String getConferenceUuid() { return conferenceUuid; }
    public String getWordNormalized() { return wordNormalized; }
    public String getMessageUuid() { return messageUuid; }
    public MessageType getMessageType() { return messageType; }
    public String getAuthorLabel() { return authorLabel; }
    public AuthorKind getAuthorKind() { return authorKind; }
    public String getDetailVisible() { return detailVisible; }
    public Instant getReceivedAt() { return receivedAt; }
    public boolean isVisible() { return visible; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setDetailVisible(String v) { this.detailVisible = v; }
    public void setVisible(boolean v) { this.visible = v; this.updatedAt = Instant.now(); }
}
