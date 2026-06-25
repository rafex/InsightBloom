package dev.rafex.insightbloom.survey.domain.model;

import java.time.Instant;

public class SurveyResponse {
    private final String uuid;
    private final String conferenceUuid;
    private final String questionUuid;
    private final String respondentToken;
    private final String answerText;
    private final Integer answerRating;
    private final Instant submittedAt;

    public SurveyResponse(final String uuid, final String conferenceUuid, final String questionUuid,
                           final String respondentToken, final String answerText, final Integer answerRating) {
        this.uuid = uuid;
        this.conferenceUuid = conferenceUuid;
        this.questionUuid = questionUuid;
        this.respondentToken = respondentToken;
        this.answerText = answerText;
        this.answerRating = answerRating;
        this.submittedAt = Instant.now();
    }

    public String getUuid() { return uuid; }
    public String getConferenceUuid() { return conferenceUuid; }
    public String getQuestionUuid() { return questionUuid; }
    public String getRespondentToken() { return respondentToken; }
    public String getAnswerText() { return answerText; }
    public Integer getAnswerRating() { return answerRating; }
    public Instant getSubmittedAt() { return submittedAt; }
}
