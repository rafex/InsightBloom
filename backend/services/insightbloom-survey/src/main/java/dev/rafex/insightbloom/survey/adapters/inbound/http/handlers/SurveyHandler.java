package dev.rafex.insightbloom.survey.adapters.inbound.http.handlers;

import dev.rafex.ether.http.core.HttpExchange;
import dev.rafex.ether.http.core.Route;
import dev.rafex.ether.http.jetty12.exchange.JettyHttpExchange;
import dev.rafex.insightbloom.common.http.BaseResourceHandler;
import dev.rafex.insightbloom.survey.application.usecases.CreateQuestionUseCase;
import dev.rafex.insightbloom.survey.application.usecases.DeactivateQuestionUseCase;
import dev.rafex.insightbloom.survey.application.usecases.DeleteConferenceDataUseCase;
import dev.rafex.insightbloom.survey.application.usecases.GetResultsUseCase;
import dev.rafex.insightbloom.survey.application.usecases.GradeResponsesUseCase;
import dev.rafex.insightbloom.survey.application.usecases.ImproveQuestionUseCase;
import dev.rafex.insightbloom.survey.application.usecases.ListQuestionsUseCase;
import dev.rafex.insightbloom.survey.application.usecases.PurgeResponsesUseCase;
import dev.rafex.insightbloom.survey.application.usecases.SubmitResponsesUseCase;
import dev.rafex.insightbloom.survey.application.usecases.SuggestQuestionsUseCase;
import dev.rafex.insightbloom.survey.application.usecases.UpdateQuestionUseCase;
import dev.rafex.insightbloom.survey.domain.ports.UsersPort;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class SurveyHandler extends BaseResourceHandler {

    private final CreateQuestionUseCase createQuestionUseCase;
    private final ListQuestionsUseCase listQuestionsUseCase;
    private final DeactivateQuestionUseCase deactivateQuestionUseCase;
    private final SubmitResponsesUseCase submitResponsesUseCase;
    private final GetResultsUseCase getResultsUseCase;
    private final SuggestQuestionsUseCase suggestQuestionsUseCase;
    private final UpdateQuestionUseCase updateQuestionUseCase;
    private final PurgeResponsesUseCase purgeResponsesUseCase;
    private final DeleteConferenceDataUseCase deleteConferenceDataUseCase;
    private final ImproveQuestionUseCase improveQuestionUseCase;
    private final GradeResponsesUseCase gradeResponsesUseCase;
    private final UsersPort usersPort;

    public SurveyHandler(final CreateQuestionUseCase createQuestionUseCase,
                          final ListQuestionsUseCase listQuestionsUseCase,
                          final DeactivateQuestionUseCase deactivateQuestionUseCase,
                          final SubmitResponsesUseCase submitResponsesUseCase,
                          final GetResultsUseCase getResultsUseCase,
                          final SuggestQuestionsUseCase suggestQuestionsUseCase,
                          final UpdateQuestionUseCase updateQuestionUseCase,
                          final PurgeResponsesUseCase purgeResponsesUseCase,
                          final DeleteConferenceDataUseCase deleteConferenceDataUseCase,
                          final ImproveQuestionUseCase improveQuestionUseCase,
                          final GradeResponsesUseCase gradeResponsesUseCase,
                          final UsersPort usersPort) {
        this.createQuestionUseCase = createQuestionUseCase;
        this.listQuestionsUseCase = listQuestionsUseCase;
        this.deactivateQuestionUseCase = deactivateQuestionUseCase;
        this.submitResponsesUseCase = submitResponsesUseCase;
        this.getResultsUseCase = getResultsUseCase;
        this.suggestQuestionsUseCase = suggestQuestionsUseCase;
        this.updateQuestionUseCase = updateQuestionUseCase;
        this.purgeResponsesUseCase = purgeResponsesUseCase;
        this.deleteConferenceDataUseCase = deleteConferenceDataUseCase;
        this.improveQuestionUseCase = improveQuestionUseCase;
        this.gradeResponsesUseCase = gradeResponsesUseCase;
        this.usersPort = usersPort;
    }

    @Override
    protected String basePath() {
        return "/api/v1/conferences";
    }

    @Override
    protected List<Route> routes() {
        return List.of(
                Route.of("/{conferenceId}/survey/questions", Set.of("GET", "POST")),
                Route.of("/{conferenceId}/survey/questions/suggest", Set.of("POST")),
                Route.of("/{conferenceId}/survey/questions/improve", Set.of("POST")),
                Route.of("/{conferenceId}/survey/questions/{questionId}/deactivate", Set.of("POST")),
                Route.of("/{conferenceId}/survey/questions/{questionId}/update", Set.of("POST")),
                Route.of("/{conferenceId}/survey/questions/{questionId}/responses/purge", Set.of("POST")),
                Route.of("/{conferenceId}/survey/responses", Set.of("POST")),
                Route.of("/{conferenceId}/survey/responded", Set.of("GET")),
                Route.of("/{conferenceId}/survey/results", Set.of("GET")),
                Route.of("/{conferenceId}/survey/grade", Set.of("POST")),
                Route.of("/{conferenceId}/survey", Set.of("DELETE")));
    }

    @Override
    public Set<String> supportedMethods() {
        return Set.of("GET", "POST", "DELETE");
    }

    @Override
    public boolean get(final HttpExchange x) {
        final var jx = asJetty(x);
        final String conferenceId = jx.pathParam("conferenceId");
        final String path = jx.path();
        try {
            if (path.endsWith("/survey/results")) {
                sendOk(jx, getResultsUseCase.execute(conferenceId));
                return true;
            }
            if (path.endsWith("/survey/questions")) {
                final boolean onlyActive = !"false".equalsIgnoreCase(queryParam(jx, "onlyActive"));
                if (onlyActive) {
                    sendOk(jx, listQuestionsUseCase.executePublic(conferenceId));
                } else {
                    sendOk(jx, listQuestionsUseCase.execute(conferenceId, false));
                }
                return true;
            }
            if (path.endsWith("/survey/responded")) {
                final String token = extractToken(jx);
                if (token == null) { sendOk(jx, Map.of("responded", false)); return true; }
                final var v = usersPort.validate(token);
                if (!v.valid() || "guest".equals(v.kind())) { sendOk(jx, Map.of("responded", false)); return true; }
                sendOk(jx, Map.of("responded", submitResponsesUseCase.hasResponded(conferenceId, v.subjectUuid())));
                return true;
            }
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
            return true;
        }
        sendError(jx, 404, "not_found", "Endpoint not found");
        return true;
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean post(final HttpExchange x) {
        final var jx = asJetty(x);
        final String conferenceId = jx.pathParam("conferenceId");
        final String path = jx.path();
        try {
            if (path.endsWith("/survey/questions/suggest")) {
                final var body = parseBody(jx);
                final int count = body.get("count") == null ? 5 : ((Number) body.get("count")).intValue();
                sendOk(jx, suggestQuestionsUseCase.execute(conferenceId, count));
                return true;
            }
            if (path.endsWith("/survey/questions/improve")) {
                final var body = parseBody(jx);
                final List<String> options = (List<String>) body.get("options");
                sendOk(jx, improveQuestionUseCase.execute(new ImproveQuestionUseCase.Request(
                        conferenceId, (String) body.get("text"), (String) body.get("type"), options,
                        (String) body.get("referenceAnswer"))));
                return true;
            }
            if (path.endsWith("/survey/questions")) {
                final var body = parseBody(jx);
                final List<String> options = (List<String>) body.get("options");
                final int orderIndex = body.get("orderIndex") == null ? 0
                        : ((Number) body.get("orderIndex")).intValue();
                final Boolean required = (Boolean) body.get("required");
                final var question = createQuestionUseCase.execute(new CreateQuestionUseCase.Request(
                        conferenceId, (String) body.get("text"), (String) body.get("type"), options,
                        (String) body.get("referenceAnswer"), (String) body.get("ratingStyle"), orderIndex, required));
                sendOk(jx, question);
                return true;
            }
            if (path.endsWith("/update")) {
                final var body = parseBody(jx);
                final List<String> options = (List<String>) body.get("options");
                final Integer orderIndex = body.get("orderIndex") == null ? null
                        : ((Number) body.get("orderIndex")).intValue();
                final Boolean required = (Boolean) body.get("required");
                final var question = updateQuestionUseCase.execute(new UpdateQuestionUseCase.Request(
                        jx.pathParam("questionId"), (String) body.get("text"), (String) body.get("type"), options,
                        (String) body.get("referenceAnswer"), (String) body.get("ratingStyle"), orderIndex, required));
                sendOk(jx, question);
                return true;
            }
            if (path.endsWith("/deactivate")) {
                deactivateQuestionUseCase.execute(jx.pathParam("questionId"));
                sendOk(jx, Map.of("status", "deactivated"));
                return true;
            }
            if (path.endsWith("/responses/purge")) {
                purgeResponsesUseCase.execute(jx.pathParam("questionId"));
                sendOk(jx, Map.of("status", "purged"));
                return true;
            }
            if (path.endsWith("/survey/grade")) {
                final String token = extractToken(jx);
                final var v = token == null ? null : usersPort.validate(token);
                if (v == null || !v.valid() || !isOrganizerOrAdmin(v.role())) {
                    sendError(jx, 403, "forbidden", "Only organizers can grade responses");
                    return true;
                }
                final var body = parseBody(jx);
                final List<String> questionUuids = (List<String>) body.get("questionUuids");
                final boolean regrade = Boolean.TRUE.equals(body.get("regrade"));
                final var result = gradeResponsesUseCase.execute(
                        new GradeResponsesUseCase.Request(conferenceId, questionUuids, regrade));
                sendOk(jx, Map.of("graded", result.graded(), "skipped", result.skipped()));
                return true;
            }
            if (path.endsWith("/survey/responses")) {
                final String token = extractToken(jx);
                if (token == null) { sendError(jx, 401, "token_missing", "Authorization required"); return true; }
                final var v = usersPort.validate(token);
                if (!v.valid() || "guest".equals(v.kind())) {
                    sendError(jx, 403, "forbidden", "You must be a verified user to answer the survey");
                    return true;
                }
                final var body = parseBody(jx);
                final List<Map<String, Object>> rawAnswers = (List<Map<String, Object>>) body.get("answers");
                final List<SubmitResponsesUseCase.Answer> answers = rawAnswers.stream()
                        .map(a -> new SubmitResponsesUseCase.Answer(
                                (String) a.get("questionUuid"),
                                (String) a.get("text"),
                                a.get("rating") == null ? null : ((Number) a.get("rating")).intValue()))
                        .toList();
                submitResponsesUseCase.execute(new SubmitResponsesUseCase.Request(conferenceId, answers, v.subjectUuid()));
                sendOk(jx, Map.of("status", "submitted"));
                return true;
            }
        } catch (final IllegalStateException e) {
            if ("llm_not_configured".equals(e.getMessage())) {
                sendError(jx, 503, e.getMessage(), "LLM grading is not configured for this deployment");
            } else {
                sendError(jx, 409, e.getMessage(), "You already answered this survey");
            }
            return true;
        } catch (final IllegalArgumentException e) {
            sendError(jx, 400, e.getMessage(), e.getMessage());
            return true;
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
            return true;
        }
        sendError(jx, 404, "not_found", "Endpoint not found");
        return true;
    }

    @Override
    public boolean delete(final HttpExchange x) {
        final var jx = asJetty(x);
        if (!validInternalAuth(jx)) { sendError(jx, 403, "forbidden", "Internal access only"); return true; }
        final String conferenceId = jx.pathParam("conferenceId");
        try {
            deleteConferenceDataUseCase.execute(conferenceId);
            sendOk(jx, Map.of("status", "deleted"));
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", "Internal error");
        }
        return true;
    }

    private static boolean isOrganizerOrAdmin(final String role) {
        return role != null && (role.contains("organizer") || role.contains("admin"));
    }

    private String extractToken(final JettyHttpExchange jx) {
        final String auth = jx.request().getHeaders().get("Authorization");
        return (auth != null && auth.startsWith("Bearer ")) ? auth.substring(7) : null;
    }
}
