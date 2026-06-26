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
    private String authorUuid;
    private Instant updatedAt;
    private String answerText;
    private Instant answeredAt;
    private String answeredByUserUuid;

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
                              String updatedByUserUuid, String authorUuid, Instant updatedAt) {
        this.uuid = uuid; this.messageUuid = messageUuid; this.conferenceUuid = conferenceUuid;
        this.wordText = wordText; this.detailText = detailText;
        this.wordStatus = wordStatus; this.detailStatus = detailStatus; this.reason = reason;
        this.editedWordValue = editedWordValue; this.editedDetailValue = editedDetailValue;
        this.updatedByUserUuid = updatedByUserUuid; this.authorUuid = authorUuid; this.updatedAt = updatedAt;
    }
    public ModerationMessage(String uuid, String messageUuid, String conferenceUuid,
                              String wordText, String detailText,
                              ContentStatus wordStatus, ContentStatus detailStatus, String reason,
                              String editedWordValue, String editedDetailValue,
                              String updatedByUserUuid, String authorUuid, Instant updatedAt,
                              String answerText, Instant answeredAt, String answeredByUserUuid) {
        this(uuid, messageUuid, conferenceUuid, wordText, detailText, wordStatus, detailStatus, reason,
                editedWordValue, editedDetailValue, updatedByUserUuid, authorUuid, updatedAt);
        this.answerText = answerText; this.answeredAt = answeredAt; this.answeredByUserUuid = answeredByUserUuid;
    }
    public String getUuid() { return uuid; }
    public String getMessageUuid() { return messageUuid; }
    public String getConferenceUuid() { return conferenceUuid; }
    public String getWordText() { return wordText; }
    public String getDetailText() { return detailText; }
    public String getAuthorUuid() { return authorUuid; }
    public void initContent(String wordText, String detailText) {
        this.wordText = wordText;
        this.detailText = detailText;
    }
    public void initAuthor(String authorUuid) {
        this.authorUuid = authorUuid;
    }
    public ContentStatus getWordStatus() { return wordStatus; }
    public ContentStatus getDetailStatus() { return detailStatus; }
    public String getReason() { return reason; }
    public String getEditedWordValue() { return editedWordValue; }
    public String getEditedDetailValue() { return editedDetailValue; }
    public String getUpdatedByUserUuid() { return updatedByUserUuid; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getAnswerText() { return answerText; }
    public Instant getAnsweredAt() { return answeredAt; }
    public String getAnsweredByUserUuid() { return answeredByUserUuid; }

    public void answer(String answerText, String answeredByUserUuid) {
        this.answerText = answerText;
        this.answeredByUserUuid = answeredByUserUuid;
        this.answeredAt = Instant.now();
        this.updatedAt = this.answeredAt;
    }

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
    public void delete(String deletedBy) {
        this.wordStatus = ContentStatus.DELETED; this.detailStatus = ContentStatus.DELETED;
        this.updatedByUserUuid = deletedBy; this.updatedAt = Instant.now();
    }
}
