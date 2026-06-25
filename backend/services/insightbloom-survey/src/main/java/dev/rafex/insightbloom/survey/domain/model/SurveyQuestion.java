package dev.rafex.insightbloom.survey.domain.model;

import java.time.Instant;
import java.util.List;

public class SurveyQuestion {
    private final String uuid;
    private final String conferenceUuid;
    private String text;
    private QuestionType type;
    private List<String> options;
    private String referenceAnswer;
    private String ratingStyle;
    private int orderIndex;
    private boolean active;
    private final Instant createdAt;
    private Instant updatedAt;

    public SurveyQuestion(final String uuid, final String conferenceUuid, final String text,
                           final QuestionType type, final List<String> options, final String referenceAnswer,
                           final String ratingStyle, final int orderIndex) {
        this.uuid = uuid;
        this.conferenceUuid = conferenceUuid;
        this.text = text;
        this.type = type;
        this.options = options;
        this.referenceAnswer = referenceAnswer;
        this.ratingStyle = ratingStyle;
        this.orderIndex = orderIndex;
        this.active = true;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public SurveyQuestion(final String uuid, final String conferenceUuid, final String text,
                           final QuestionType type, final List<String> options, final String referenceAnswer,
                           final String ratingStyle, final int orderIndex, final boolean active,
                           final Instant createdAt, final Instant updatedAt) {
        this.uuid = uuid;
        this.conferenceUuid = conferenceUuid;
        this.text = text;
        this.type = type;
        this.options = options;
        this.referenceAnswer = referenceAnswer;
        this.ratingStyle = ratingStyle;
        this.orderIndex = orderIndex;
        this.active = active;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public void deactivate() { this.active = false; this.updatedAt = Instant.now(); }

    public void update(final String text, final QuestionType type, final List<String> options,
                        final String referenceAnswer, final String ratingStyle, final int orderIndex) {
        this.text = text;
        this.type = type;
        this.options = options;
        this.referenceAnswer = referenceAnswer;
        this.ratingStyle = ratingStyle;
        this.orderIndex = orderIndex;
        this.updatedAt = Instant.now();
    }

    public String getUuid() { return uuid; }
    public String getConferenceUuid() { return conferenceUuid; }
    public String getText() { return text; }
    public QuestionType getType() { return type; }
    public List<String> getOptions() { return options; }
    public String getReferenceAnswer() { return referenceAnswer; }
    public String getRatingStyle() { return ratingStyle; }
    public int getOrderIndex() { return orderIndex; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
