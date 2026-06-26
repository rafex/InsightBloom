package dev.rafex.insightbloom.survey.adapters.inbound.http.handlers;

import dev.rafex.ether.http.core.HttpExchange;
import dev.rafex.ether.http.core.Route;
import dev.rafex.ether.http.jetty12.response.JettyApiResponses;
import dev.rafex.ether.http.jetty12.exchange.JettyHttpExchange;
import dev.rafex.ether.http.jetty12.handler.NonBlockingResourceHandler;
import dev.rafex.ether.json.JsonCodec;
import dev.rafex.ether.json.JsonUtils;
import dev.rafex.insightbloom.contracts.ApiError;
import dev.rafex.insightbloom.contracts.ApiMeta;
import dev.rafex.insightbloom.contracts.ApiResponse;
import dev.rafex.insightbloom.survey.application.usecases.CreateQuestionUseCase;
import dev.rafex.insightbloom.survey.application.usecases.DeactivateQuestionUseCase;
import dev.rafex.insightbloom.survey.application.usecases.DeleteConferenceDataUseCase;
import dev.rafex.insightbloom.survey.application.usecases.GetResultsUseCase;
import dev.rafex.insightbloom.survey.application.usecases.ImproveQuestionUseCase;
import dev.rafex.insightbloom.survey.application.usecases.ListQuestionsUseCase;
import dev.rafex.insightbloom.survey.application.usecases.PurgeResponsesUseCase;
import dev.rafex.insightbloom.survey.application.usecases.SubmitResponsesUseCase;
import dev.rafex.insightbloom.survey.application.usecases.SuggestQuestionsUseCase;
import dev.rafex.insightbloom.survey.application.usecases.UpdateQuestionUseCase;
import dev.rafex.insightbloom.survey.domain.ports.UsersPort;
import org.eclipse.jetty.server.Request;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class SurveyHandler extends NonBlockingResourceHandler {

    private static final JsonCodec JSON_CODEC = JsonUtils.codec();
    private static final JettyApiResponses RESPONSES = new JettyApiResponses(JSON_CODEC);

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
                          final UsersPort usersPort) {
        super(JSON_CODEC);
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
                final var body = JSON_CODEC.readValue(Request.asInputStream(jx.request()), Map.class);
                final int count = body.get("count") == null ? 5 : ((Number) body.get("count")).intValue();
                sendOk(jx, suggestQuestionsUseCase.execute(conferenceId, count));
                return true;
            }
            if (path.endsWith("/survey/questions/improve")) {
                final var body = JSON_CODEC.readValue(Request.asInputStream(jx.request()), Map.class);
                final List<String> options = (List<String>) body.get("options");
                sendOk(jx, improveQuestionUseCase.execute(new ImproveQuestionUseCase.Request(
                        conferenceId, (String) body.get("text"), (String) body.get("type"), options,
                        (String) body.get("referenceAnswer"))));
                return true;
            }
            if (path.endsWith("/survey/questions")) {
                final var body = JSON_CODEC.readValue(Request.asInputStream(jx.request()), Map.class);
                final List<String> options = (List<String>) body.get("options");
                final int orderIndex = body.get("orderIndex") == null ? 0
                        : ((Number) body.get("orderIndex")).intValue();
                final var question = createQuestionUseCase.execute(new CreateQuestionUseCase.Request(
                        conferenceId, (String) body.get("text"), (String) body.get("type"), options,
                        (String) body.get("referenceAnswer"), (String) body.get("ratingStyle"), orderIndex));
                sendOk(jx, question);
                return true;
            }
            if (path.endsWith("/update")) {
                final var body = JSON_CODEC.readValue(Request.asInputStream(jx.request()), Map.class);
                final List<String> options = (List<String>) body.get("options");
                final Integer orderIndex = body.get("orderIndex") == null ? null
                        : ((Number) body.get("orderIndex")).intValue();
                final var question = updateQuestionUseCase.execute(new UpdateQuestionUseCase.Request(
                        jx.pathParam("questionId"), (String) body.get("text"), (String) body.get("type"), options,
                        (String) body.get("referenceAnswer"), (String) body.get("ratingStyle"), orderIndex));
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
            if (path.endsWith("/survey/responses")) {
                final String token = extractToken(jx);
                if (token == null) { sendError(jx, 401, "token_missing", "Authorization required"); return true; }
                final var v = usersPort.validate(token);
                if (!v.valid() || "guest".equals(v.kind())) {
                    sendError(jx, 403, "forbidden", "You must be a verified user to answer the survey");
                    return true;
                }
                final var body = JSON_CODEC.readValue(Request.asInputStream(jx.request()), Map.class);
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
            sendError(jx, 409, e.getMessage(), "You already answered this survey");
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

    private <T> void sendOk(final JettyHttpExchange jx, final T data) {
        RESPONSES.json(jx.response(), jx.callback(), 200,
                new ApiResponse<>(data, ApiMeta.of(UUID.randomUUID().toString())));
    }

    private void sendError(final JettyHttpExchange jx, final int status, final String code, final String message) {
        RESPONSES.json(jx.response(), jx.callback(), status,
                ApiError.of(code, message, UUID.randomUUID().toString()));
    }

    @Override
    public boolean delete(final HttpExchange x) {
        final var jx = asJetty(x);
        final String conferenceId = jx.pathParam("conferenceId");
        try {
            deleteConferenceDataUseCase.execute(conferenceId);
            sendOk(jx, Map.of("status", "deleted"));
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
        }
        return true;
    }

    private String extractToken(final JettyHttpExchange jx) {
        final String auth = jx.request().getHeaders().get("Authorization");
        return (auth != null && auth.startsWith("Bearer ")) ? auth.substring(7) : null;
    }

    private static JettyHttpExchange asJetty(final HttpExchange x) {
        return (JettyHttpExchange) x;
    }
}
