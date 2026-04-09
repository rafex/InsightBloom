package dev.rafex.insightbloom.moderation.domain.model;
import java.time.Instant;
import java.util.UUID;
public class ModerationMessage {
    private final String uuid;
    private final String messageUuid;
    private final String conferenceUuid;
    private String wordText;
    private String detailText;
    private ContentStatus wordStatus;
    private ContentStatus detailStatus;
    private String reason;
    private String editedWordValue;
    private String editedDetailValue;
    private String updatedByUserUuid;
    private Instant updatedAt;

    public ModerationMessage(String messageUuid, String conferenceUuid) {
        this.uuid = UUID.randomUUID().toString();
        this.messageUuid = messageUuid;
        this.conferenceUuid = conferenceUuid;
        this.wordText = null;
        this.detailText = null;
        this.wordStatus = ContentStatus.VISIBLE;
        this.detailStatus = ContentStatus.VISIBLE;
        this.updatedAt = Instant.now();
    }
    public ModerationMessage(String uuid, String messageUuid, String conferenceUuid,
                              String wordText, String detailText,
                              ContentStatus wordStatus, ContentStatus detailStatus, String reason,
                              String editedWordValue, String editedDetailValue,
                              String updatedByUserUuid, Instant updatedAt) {
        this.uuid = uuid; this.messageUuid = messageUuid; this.conferenceUuid = conferenceUuid;
        this.wordText = wordText; this.detailText = detailText;
        this.wordStatus = wordStatus; this.detailStatus = detailStatus; this.reason = reason;
        this.editedWordValue = editedWordValue; this.editedDetailValue = editedDetailValue;
        this.updatedByUserUuid = updatedByUserUuid; this.updatedAt = updatedAt;
    }
    public String getUuid() { return uuid; }
    public String getMessageUuid() { return messageUuid; }
    public String getConferenceUuid() { return conferenceUuid; }
    public String getWordText() { return wordText; }
    public String getDetailText() { return detailText; }
    public void initContent(String wordText, String detailText) {
        this.wordText = wordText;
        this.detailText = detailText;
    }
    public ContentStatus getWordStatus() { return wordStatus; }
    public ContentStatus getDetailStatus() { return detailStatus; }
    public String getReason() { return reason; }
    public String getEditedWordValue() { return editedWordValue; }
    public String getEditedDetailValue() { return editedDetailValue; }
    public String getUpdatedByUserUuid() { return updatedByUserUuid; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void censorWord(String reason, String updatedBy) {
        this.wordStatus = ContentStatus.CENSURADO_MANUAL; this.reason = reason;
        this.updatedByUserUuid = updatedBy; this.updatedAt = Instant.now();
    }
    public void censorDetail(String reason, String updatedBy) {
        this.detailStatus = ContentStatus.CENSURADO_MANUAL; this.reason = reason;
        this.updatedByUserUuid = updatedBy; this.updatedAt = Instant.now();
    }
    public void restore(String updatedBy) {
        this.wordStatus = ContentStatus.VISIBLE; this.detailStatus = ContentStatus.VISIBLE;
        this.reason = null; this.updatedByUserUuid = updatedBy; this.updatedAt = Instant.now();
    }
    public void edit(String editedWord, String editedDetail, String updatedBy) {
        this.editedWordValue = editedWord; this.editedDetailValue = editedDetail;
        this.updatedByUserUuid = updatedBy; this.updatedAt = Instant.now();
    }
}
