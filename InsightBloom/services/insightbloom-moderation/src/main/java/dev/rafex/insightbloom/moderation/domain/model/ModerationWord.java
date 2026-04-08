package dev.rafex.insightbloom.moderation.domain.model;
import java.time.Instant;
import java.util.UUID;
public class ModerationWord {
    private final String uuid;
    private final String conferenceUuid;
    private final String wordNormalized;
    private String wordCanonical;
    private ContentStatus status;
    private String reason;
    private String editedValue;
    private String updatedByUserUuid;
    private Instant updatedAt;

    public ModerationWord(String conferenceUuid, String wordNormalized, String wordCanonical) {
        this.uuid = UUID.randomUUID().toString();
        this.conferenceUuid = conferenceUuid;
        this.wordNormalized = wordNormalized;
        this.wordCanonical = wordCanonical;
        this.status = ContentStatus.VISIBLE;
        this.updatedAt = Instant.now();
    }
    public ModerationWord(String conferenceUuid, String wordNormalized, String wordCanonical, ContentStatus status) {
        this(conferenceUuid, wordNormalized, wordCanonical);
        this.status = status;
    }
    public ModerationWord(String uuid, String conferenceUuid, String wordNormalized, String wordCanonical,
                          ContentStatus status, String reason, String editedValue,
                          String updatedByUserUuid, Instant updatedAt) {
        this.uuid = uuid; this.conferenceUuid = conferenceUuid; this.wordNormalized = wordNormalized;
        this.wordCanonical = wordCanonical; this.status = status; this.reason = reason;
        this.editedValue = editedValue; this.updatedByUserUuid = updatedByUserUuid; this.updatedAt = updatedAt;
    }
    public String getUuid() { return uuid; }
    public String getConferenceUuid() { return conferenceUuid; }
    public String getWordNormalized() { return wordNormalized; }
    public String getWordCanonical() { return wordCanonical; }
    public ContentStatus getStatus() { return status; }
    public String getReason() { return reason; }
    public String getEditedValue() { return editedValue; }
    public String getUpdatedByUserUuid() { return updatedByUserUuid; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void censor(String reason, String updatedBy) {
        this.status = ContentStatus.CENSURADO_MANUAL; this.reason = reason;
        this.updatedByUserUuid = updatedBy; this.updatedAt = Instant.now();
    }
    public void restore(String updatedBy) {
        this.status = ContentStatus.VISIBLE; this.reason = null;
        this.updatedByUserUuid = updatedBy; this.updatedAt = Instant.now();
    }
    public void edit(String newValue, String updatedBy) {
        this.editedValue = newValue; this.updatedByUserUuid = updatedBy; this.updatedAt = Instant.now();
    }
}
