package dev.rafex.insightbloom.survey.domain.model;

import java.time.Instant;

public class SurveyResponse {
    private final String uuid;
    private final String conferenceUuid;
    private final String questionUuid;
    private final String respondentToken;
    private final String answerText;
    private final Integer answerRating;
    private Double gradeScore;
    private String gradeFeedback;
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

    public SurveyResponse(final String uuid, final String conferenceUuid, final String questionUuid,
                           final String respondentToken, final String answerText, final Integer answerRating,
                           final Double gradeScore, final String gradeFeedback, final Instant submittedAt) {
        this.uuid = uuid;
        this.conferenceUuid = conferenceUuid;
        this.questionUuid = questionUuid;
        this.respondentToken = respondentToken;
        this.answerText = answerText;
        this.answerRating = answerRating;
        this.gradeScore = gradeScore;
        this.gradeFeedback = gradeFeedback;
        this.submittedAt = submittedAt;
    }

    public void grade(final Double score, final String feedback) {
        this.gradeScore = score;
        this.gradeFeedback = feedback;
    }

    public String getUuid() { return uuid; }
    public String getConferenceUuid() { return conferenceUuid; }
    public String getQuestionUuid() { return questionUuid; }
    public String getRespondentToken() { return respondentToken; }
    public String getAnswerText() { return answerText; }
    public Integer getAnswerRating() { return answerRating; }
    public Double getGradeScore() { return gradeScore; }
    public String getGradeFeedback() { return gradeFeedback; }
    public Instant getSubmittedAt() { return submittedAt; }
}
