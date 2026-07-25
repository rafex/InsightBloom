package dev.rafex.insightbloom.survey.adapters.inbound.http.handlers;

import dev.rafex.ether.http.core.HttpExchange;
import dev.rafex.ether.http.core.Route;
import dev.rafex.ether.http.jetty12.exchange.JettyHttpExchange;
import dev.rafex.ether.json.JsonUtils;
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
import dev.rafex.insightbloom.survey.application.usecases.SubmitSurveyJsSubmissionUseCase;
import dev.rafex.insightbloom.survey.application.usecases.SuggestQuestionsUseCase;
import dev.rafex.insightbloom.survey.application.usecases.SurveyDefinitionUseCase;
import dev.rafex.insightbloom.survey.application.usecases.SurveyAccessUseCase;
import dev.rafex.insightbloom.survey.application.usecases.UpdateQuestionUseCase;
import dev.rafex.insightbloom.survey.application.usecases.AiMentorConfigUseCase;
import dev.rafex.insightbloom.survey.application.usecases.AiSurveyConfigUseCase;
import dev.rafex.insightbloom.survey.application.usecases.MentorChatUseCase;
import dev.rafex.insightbloom.survey.domain.model.AiMentorConfig;
import dev.rafex.insightbloom.survey.domain.model.AiSurveyConfig;
import dev.rafex.insightbloom.survey.domain.model.SurveyDefinition;
import dev.rafex.insightbloom.survey.domain.model.SurveyEngine;
import dev.rafex.insightbloom.survey.domain.model.SurveyJsSubmission;
import dev.rafex.insightbloom.survey.domain.ports.SurveyDefinitionRepository;
import dev.rafex.insightbloom.survey.domain.ports.SurveyJsSubmissionRepository;
import dev.rafex.insightbloom.survey.domain.ports.UsersPort;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    private final SurveyDefinitionUseCase surveyDefinitionUseCase;
    private final SubmitSurveyJsSubmissionUseCase submitSurveyJsSubmissionUseCase;
    private final SurveyDefinitionRepository surveyDefinitionRepository;
    private final SurveyJsSubmissionRepository surveyJsSubmissionRepository;
    private final UsersPort usersPort;
    private final SurveyAccessUseCase surveyAccessUseCase;
    private final AiMentorConfigUseCase aiMentorConfigUseCase;
    private final MentorChatUseCase mentorChatUseCase;
    private final AiSurveyConfigUseCase aiSurveyConfigUseCase;

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
                          final SurveyDefinitionUseCase surveyDefinitionUseCase,
                          final SubmitSurveyJsSubmissionUseCase submitSurveyJsSubmissionUseCase,
                          final SurveyDefinitionRepository surveyDefinitionRepository,
                          final SurveyJsSubmissionRepository surveyJsSubmissionRepository,
                          final UsersPort usersPort,
                          final SurveyAccessUseCase surveyAccessUseCase,
                          final AiMentorConfigUseCase aiMentorConfigUseCase,
                          final MentorChatUseCase mentorChatUseCase,
                          final AiSurveyConfigUseCase aiSurveyConfigUseCase) {
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
        this.surveyDefinitionUseCase = surveyDefinitionUseCase;
        this.submitSurveyJsSubmissionUseCase = submitSurveyJsSubmissionUseCase;
        this.surveyDefinitionRepository = surveyDefinitionRepository;
        this.surveyJsSubmissionRepository = surveyJsSubmissionRepository;
        this.usersPort = usersPort;
        this.surveyAccessUseCase = surveyAccessUseCase;
        this.aiMentorConfigUseCase = aiMentorConfigUseCase;
        this.mentorChatUseCase = mentorChatUseCase;
        this.aiSurveyConfigUseCase = aiSurveyConfigUseCase;
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
                Route.of("/{conferenceId}/survey/definition", Set.of("GET", "PUT")),
                Route.of("/{conferenceId}/survey/definition/engine", Set.of("POST")),
                Route.of("/{conferenceId}/survey/definition/validate", Set.of("POST")),
                Route.of("/{conferenceId}/survey/definition/publish", Set.of("POST")),
                Route.of("/{conferenceId}/survey/access", Set.of("GET")),
                Route.of("/{conferenceId}/survey/access-management", Set.of("GET")),
                Route.of("/{conferenceId}/survey/access/release", Set.of("POST")),
                Route.of("/{conferenceId}/survey/responses", Set.of("POST")),
                Route.of("/{conferenceId}/survey/submissions", Set.of("GET", "POST")),
                Route.of("/{conferenceId}/survey/responded", Set.of("GET")),
                Route.of("/{conferenceId}/survey/results", Set.of("GET")),
                Route.of("/{conferenceId}/survey/grade", Set.of("POST")),
                Route.of("/{conferenceId}/mentor/config", Set.of("GET", "PUT")),
                Route.of("/{conferenceId}/mentor/chat", Set.of("POST")),
                Route.of("/{conferenceId}/survey/ai-config", Set.of("GET", "PUT")),
                Route.of("/{conferenceId}/survey", Set.of("DELETE")));
    }

    @Override
    public Set<String> supportedMethods() {
        return Set.of("GET", "POST", "PUT", "DELETE");
    }

    @Override
    public boolean get(final HttpExchange x) {
        final var jx = asJetty(x);
        final String conferenceId = jx.pathParam("conferenceId");
        final String path = jx.path();
        try {
            if (path.endsWith("/mentor/config")) {
                if (requireSurveyManager(jx, conferenceId) == null) return true;
                sendOk(jx, mentorConfigView(aiMentorConfigUseCase.get(conferenceId)));
                return true;
            }
            if (path.endsWith("/survey/ai-config")) {
                if (requireSurveyManager(jx, conferenceId) == null) return true;
                sendOk(jx, surveyConfigView(aiSurveyConfigUseCase.get(conferenceId)));
                return true;
            }
            if (path.endsWith("/survey/access-management")) {
                if (requireSurveyManager(jx, conferenceId) == null) return true;
                final String token = extractToken(jx);
                final boolean releasedForAll = surveyAccessUseCase.isReleasedForAll(conferenceId);
                final List<Map<String, Object>> attendees = new ArrayList<>();
                for (final var attendee : usersPort.listConferenceAttendees(conferenceId, token)) {
                    final boolean released = releasedForAll || surveyAccessUseCase.isReleased(conferenceId, attendee.uuid());
                    attendees.add(Map.ofEntries(
                            Map.entry("uuid", attendee.uuid()),
                            Map.entry("displayName", attendee.displayName() == null ? "" : attendee.displayName()),
                            Map.entry("email", attendee.email() == null ? "" : attendee.email()),
                            Map.entry("joinedAt", attendee.joinedAt() == null ? "" : attendee.joinedAt()),
                            Map.entry("released", released),
                            Map.entry("responded", submitResponsesUseCase.hasResponded(conferenceId, attendee.uuid())
                                    || submitSurveyJsSubmissionUseCase.hasResponded(conferenceId, attendee.uuid()))));
                }
                sendOk(jx, Map.of("releasedForAll", releasedForAll, "attendees", attendees));
                return true;
            }
            if (path.endsWith("/survey/access")) {
                final String token = extractToken(jx);
                if (token == null) { sendError(jx, 401, "token_missing", "Authorization required"); return true; }
                final var v = usersPort.validate(token);
                if (!v.valid() || "guest".equals(v.kind())) {
                    sendError(jx, 403, "forbidden", "You must be a verified user to access the survey");
                    return true;
                }
                if (!usersPort.hasConferenceAccess(conferenceId, token)) {
                    sendError(jx, 403, "ticket_required", "Necesitas un boleto canjeado para responder esta encuesta");
                    return true;
                }
                final boolean surveyJs = isSurveyJs(conferenceId);
                final boolean published = !surveyJs || surveyDefinitionRepository.findByConference(conferenceId)
                        .map(definition -> "PUBLISHED".equals(definition.getStatus())).orElse(false);
                sendOk(jx, Map.of("released", surveyAccessUseCase.isReleased(conferenceId, v.subjectUuid()),
                        "releasedForAll", surveyAccessUseCase.isReleasedForAll(conferenceId),
                        "responded", submitResponsesUseCase.hasResponded(conferenceId, v.subjectUuid())
                                || submitSurveyJsSubmissionUseCase.hasResponded(conferenceId, v.subjectUuid()),
                        "published", published));
                return true;
            }
            if (path.endsWith("/survey/definition")) {
                final boolean draft = "true".equalsIgnoreCase(queryParam(jx, "draft"));
                if (draft && requireConferenceOwner(jx, conferenceId) == null) return true;
                sendOk(jx, definitionView(surveyDefinitionUseCase.get(conferenceId, !draft)));
                return true;
            }
            if (path.endsWith("/survey/submissions")) {
                if (requireConferenceOwner(jx, conferenceId) == null) return true;
                sendOk(jx, submissionViews(surveyJsSubmissionRepository.findByConference(conferenceId)));
                return true;
            }
            if (path.endsWith("/survey/results")) {
                if (requireConferenceOwner(jx, conferenceId) == null) return true;
                if (isSurveyJs(conferenceId)) {
                    sendOk(jx, submissionViews(surveyJsSubmissionRepository.findByConference(conferenceId)));
                    return true;
                }
                sendOk(jx, getResultsUseCase.execute(conferenceId));
                return true;
            }
            if (path.endsWith("/survey/questions")) {
                if (isSurveyJs(conferenceId)) { sendOk(jx, List.of()); return true; }
                final boolean onlyActive = !"false".equalsIgnoreCase(queryParam(jx, "onlyActive"));
                if (onlyActive) {
                    sendOk(jx, listQuestionsUseCase.executePublic(conferenceId));
                } else {
                    // AUD-05: onlyActive=false devuelve el modelo completo (incluye
                    // referenceAnswer, estado y metadatos internos de preguntas inactivas) --
                    // debe quedar restringido a quien gestiona la encuesta, no publico.
                    if (requireSurveyManager(jx, conferenceId) == null) return true;
                    sendOk(jx, listQuestionsUseCase.execute(conferenceId, false));
                }
                return true;
            }
            if (path.endsWith("/survey/responded")) {
                final String token = extractToken(jx);
                if (token == null) { sendOk(jx, Map.of("responded", false)); return true; }
                final var v = usersPort.validate(token);
                if (!v.valid() || "guest".equals(v.kind())) { sendOk(jx, Map.of("responded", false)); return true; }
                final String queriedUserUuid = queryParam(jx, "userUuid");
                // Un organizador/admin puede consultar el status de otro usuario (panel de detalle
                // de usuario); cualquier otro caller solo puede ver el suyo propio.
                final String targetUuid = (queriedUserUuid != null && isOrganizerOrAdmin(v.role()))
                        ? queriedUserUuid : v.subjectUuid();
                sendOk(jx, Map.of("responded", submitResponsesUseCase.hasResponded(conferenceId, targetUuid)
                        || submitSurveyJsSubmissionUseCase.hasResponded(conferenceId, targetUuid)));
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
            if (path.endsWith("/mentor/chat")) {
                final String token = extractToken(jx);
                if (token == null) { sendError(jx, 401, "token_missing", "Authorization required"); return true; }
                final var validation = usersPort.validate(token);
                if (validation == null || !validation.valid()) {
                    sendError(jx, 401, "token_invalid", "Invalid token"); return true;
                }
                if (!usersPort.hasConferenceAccess(conferenceId, token)) {
                    sendError(jx, 403, "ticket_required", "Necesitas un boleto canjeado para usar el tutor IA");
                    return true;
                }
                final var body = parseBody(jx);
                sendOk(jx, mentorChatUseCase.execute(new MentorChatUseCase.Request(
                        conferenceId, validation.subjectUuid(), (String) body.get("message"),
                        (String) body.get("fileName"), (String) body.get("codeContext"),
                        mentorHistory(body.get("history")))));
                return true;
            }
            if (path.endsWith("/survey/access/release")) {
                if (requireSurveyManager(jx, conferenceId) == null) return true;
                final var body = parseBody(jx);
                if (Boolean.TRUE.equals(body.get("all"))) {
                    surveyAccessUseCase.releaseForAll(conferenceId);
                    sendOk(jx, Map.of("releasedForAll", true, "releasedCount", 0));
                    return true;
                }
                final Object rawUsers = body.get("userUuids");
                if (!(rawUsers instanceof List<?>)) throw new IllegalArgumentException("user_uuids_required");
                final List<String> userUuids = ((List<?>) rawUsers).stream().filter(String.class::isInstance)
                        .map(String.class::cast).distinct().toList();
                final var registered = usersPort.listConferenceAttendees(conferenceId, extractToken(jx)).stream()
                        .map(UsersPort.AttendeeSummary::uuid).collect(java.util.stream.Collectors.toSet());
                if (userUuids.stream().anyMatch(uuid -> !registered.contains(uuid))) {
                    sendError(jx, 404, "attendee_not_registered", "El asistente no está registrado en esta conferencia");
                    return true;
                }
                surveyAccessUseCase.releaseUsers(conferenceId, userUuids);
                sendOk(jx, Map.of("releasedForAll", surveyAccessUseCase.isReleasedForAll(conferenceId),
                        "releasedCount", userUuids.size()));
                return true;
            }
            if (path.endsWith("/survey/definition/engine")) {
                if (requireConferenceOwner(jx, conferenceId) == null) return true;
                final var body = parseBody(jx);
                final SurveyEngine engine = SurveyEngine.valueOf(String.valueOf(body.get("engine")).toUpperCase());
                sendOk(jx, definitionView(Optional.of(surveyDefinitionUseCase.selectEngine(conferenceId, engine))));
                return true;
            }
            if (path.endsWith("/survey/definition/validate")) {
                if (requireConferenceOwner(jx, conferenceId) == null) return true;
                final var body = parseBody(jx);
                surveyDefinitionUseCase.validate(schemaFromBody(body));
                sendOk(jx, Map.of("valid", true));
                return true;
            }
            if (path.endsWith("/survey/definition/publish")) {
                if (requireConferenceOwner(jx, conferenceId) == null) return true;
                final var body = parseBody(jx);
                sendOk(jx, definitionView(Optional.of(
                        surveyDefinitionUseCase.publish(conferenceId, schemaFromBody(body)))));
                return true;
            }
            if (path.endsWith("/survey/questions/suggest")) {
                if (requireConferenceOwner(jx, conferenceId) == null) return true;
                final var body = parseBody(jx);
                final int count = body.get("count") == null ? 5 : ((Number) body.get("count")).intValue();
                sendOk(jx, suggestQuestionsUseCase.execute(conferenceId, count));
                return true;
            }
            if (path.endsWith("/survey/questions/improve")) {
                if (requireConferenceOwner(jx, conferenceId) == null) return true;
                final var body = parseBody(jx);
                final List<String> options = (List<String>) body.get("options");
                sendOk(jx, improveQuestionUseCase.execute(new ImproveQuestionUseCase.Request(
                        conferenceId, (String) body.get("text"), (String) body.get("type"), options,
                        (String) body.get("referenceAnswer"))));
                return true;
            }
            if (path.endsWith("/survey/questions")) {
                if (requireConferenceOwner(jx, conferenceId) == null) return true;
                if (isSurveyJs(conferenceId)) {
                    sendError(jx, 409, "engine_immutable", "This conference uses SurveyJS");
                    return true;
                }
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
                if (requireConferenceOwner(jx, conferenceId) == null) return true;
                if (isSurveyJs(conferenceId)) {
                    sendError(jx, 409, "engine_immutable", "This conference uses SurveyJS");
                    return true;
                }
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
                if (requireConferenceOwner(jx, conferenceId) == null) return true;
                deactivateQuestionUseCase.execute(jx.pathParam("questionId"));
                sendOk(jx, Map.of("status", "deactivated"));
                return true;
            }
            if (path.endsWith("/responses/purge")) {
                if (requireConferenceOwner(jx, conferenceId) == null) return true;
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
                if (!usersPort.hasConferenceAccess(conferenceId, token)) {
                    sendError(jx, 403, "ticket_required", "Necesitas un boleto canjeado para responder esta encuesta");
                    return true;
                }
                if (!surveyAccessUseCase.isReleased(conferenceId, v.subjectUuid())) {
                    sendError(jx, 423, "survey_locked", "La encuesta aún no ha sido liberada por el moderador");
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
            if (path.endsWith("/survey/submissions")) {
                final String token = extractToken(jx);
                if (token == null) { sendError(jx, 401, "token_missing", "Authorization required"); return true; }
                final var v = usersPort.validate(token);
                if (!v.valid() || "guest".equals(v.kind())) {
                    sendError(jx, 403, "forbidden", "You must be a verified user to answer the survey");
                    return true;
                }
                if (!usersPort.hasConferenceAccess(conferenceId, token)) {
                    sendError(jx, 403, "ticket_required", "Necesitas un boleto canjeado para responder esta encuesta");
                    return true;
                }
                if (!surveyAccessUseCase.isReleased(conferenceId, v.subjectUuid())) {
                    sendError(jx, 423, "survey_locked", "La encuesta aún no ha sido liberada por el moderador");
                    return true;
                }
                final var body = parseBody(jx);
                submitSurveyJsSubmissionUseCase.execute(conferenceId, v.subjectUuid(), schemaFromDataBody(body));
                sendOk(jx, Map.of("status", "submitted"));
                return true;
            }
        } catch (final IllegalStateException e) {
            if ("llm_not_configured".equals(e.getMessage())) {
                sendError(jx, 503, e.getMessage(), "El tutor IA no está configurado en este despliegue");
            } else if ("mentor_disabled".equals(e.getMessage())) {
                sendError(jx, 423, e.getMessage(), "El tutor IA está deshabilitado para este evento");
            } else if ("mentor_rate_limited".equals(e.getMessage())) {
                sendError(jx, 429, e.getMessage(), "Demasiadas consultas; intenta nuevamente en un minuto");
            } else if ("empty_llm_reply".equals(e.getMessage())) {
                sendError(jx, 502, e.getMessage(), "El proveedor de IA no devolvió una respuesta");
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
    @SuppressWarnings("unchecked")
    public boolean put(final HttpExchange x) {
        final var jx = asJetty(x);
        final String conferenceId = jx.pathParam("conferenceId");
        if (jx.path().endsWith("/mentor/config")) {
            try {
                if (requireSurveyManager(jx, conferenceId) == null) return true;
                final var body = parseBody(jx);
                final var config = aiMentorConfigUseCase.save(
                        conferenceId,
                        Boolean.TRUE.equals(body.get("enabled")),
                        (String) body.get("objective"),
                        (String) body.get("prompt"),
                        !Boolean.FALSE.equals(body.get("includePresentation")),
                        body.get("maxRequestsPerMinute") == null ? 8
                                : ((Number) body.get("maxRequestsPerMinute")).intValue());
                sendOk(jx, mentorConfigView(config));
            } catch (final IllegalArgumentException e) {
                sendError(jx, 400, e.getMessage(), e.getMessage());
            } catch (final Exception e) {
                sendError(jx, 500, "internal_error", e.getMessage());
            }
            return true;
        }
        if (jx.path().endsWith("/survey/ai-config")) {
            try {
                if (requireSurveyManager(jx, conferenceId) == null) return true;
                final var body = parseBody(jx);
                final var config = aiSurveyConfigUseCase.save(conferenceId, (String) body.get("extraContext"));
                sendOk(jx, surveyConfigView(config));
            } catch (final IllegalArgumentException e) {
                sendError(jx, 400, e.getMessage(), e.getMessage());
            } catch (final Exception e) {
                sendError(jx, 500, "internal_error", e.getMessage());
            }
            return true;
        }
        if (!jx.path().endsWith("/survey/definition")) {
            sendError(jx, 404, "not_found", "Endpoint not found");
            return true;
        }
        try {
            if (requireConferenceOwner(jx, conferenceId) == null) return true;
            final var body = parseBody(jx);
            sendOk(jx, definitionView(Optional.of(
                    surveyDefinitionUseCase.saveDraft(conferenceId, schemaFromBody(body)))));
        } catch (final IllegalStateException e) {
            sendError(jx, 409, e.getMessage(), e.getMessage());
        } catch (final IllegalArgumentException e) {
            sendError(jx, 400, e.getMessage(), e.getMessage());
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
        }
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
        return legacyRoleHasAny(role, "organizer", "admin");
    }

    private boolean isSurveyJs(final String conferenceId) {
        return surveyDefinitionRepository.findByConference(conferenceId)
                .map(d -> d.getEngine() == SurveyEngine.SURVEYJS).orElse(false);
    }

    private Map<String, Object> definitionView(final Optional<SurveyDefinition> definition) {
        final Map<String, Object> result = new HashMap<>();
        result.put("configured", definition.isPresent());
        if (definition.isEmpty()) {
            result.put("engine", null);
            result.put("schema", null);
            return result;
        }
        final SurveyDefinition value = definition.get();
        result.put("uuid", value.getUuid());
        result.put("conferenceUuid", value.getConferenceUuid());
        result.put("engine", value.getEngine().name());
        result.put("schemaVersion", value.getSchemaVersion());
        result.put("status", value.getStatus());
        result.put("updatedAt", value.getUpdatedAt().toString());
        result.put("publishedAt", value.getPublishedAt() == null ? null : value.getPublishedAt().toString());
        result.put("schema", value.getEngine() == SurveyEngine.SURVEYJS
                ? JsonUtils.codec().readValue(value.getSchemaJson(), Map.class) : null);
        return result;
    }

    private Map<String, Object> mentorConfigView(final AiMentorConfig config) {
        return Map.ofEntries(
                Map.entry("conferenceUuid", config.conferenceUuid()),
                Map.entry("enabled", config.enabled()),
                Map.entry("objective", config.objective() == null ? "" : config.objective()),
                Map.entry("prompt", config.prompt() == null ? "" : config.prompt()),
                Map.entry("includePresentation", config.includePresentation()),
                Map.entry("maxRequestsPerMinute", config.maxRequestsPerMinute()),
                Map.entry("updatedAt", config.updatedAt().toString()));
    }

    private Map<String, Object> surveyConfigView(final AiSurveyConfig config) {
        return Map.of(
                "conferenceUuid", config.conferenceUuid(),
                "extraContext", config.extraContext() == null ? "" : config.extraContext(),
                "updatedAt", config.updatedAt().toString());
    }

    private List<MentorChatUseCase.ChatMessage> mentorHistory(final Object raw) {
        if (!(raw instanceof List<?> values)) return List.of();
        return values.stream().limit(8).filter(Map.class::isInstance).map(Map.class::cast)
                .map(item -> new MentorChatUseCase.ChatMessage(
                        String.valueOf(item.get("role")), String.valueOf(item.get("content"))))
                .toList();
    }

    private List<Map<String, Object>> submissionViews(final List<SurveyJsSubmission> submissions) {
        final List<Map<String, Object>> result = new ArrayList<>();
        for (final SurveyJsSubmission submission : submissions) {
            result.add(Map.of("uuid", submission.uuid(), "userUuid", submission.userUuid(),
                    "definitionVersion", submission.definitionVersion(),
                    "submittedAt", submission.submittedAt().toString(),
                    "data", JsonUtils.codec().readValue(submission.payloadJson(), Map.class)));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> schemaFromBody(final Map<String, Object> body) {
        final Object schema = body.get("schema");
        if (!(schema instanceof Map<?, ?>)) throw new IllegalArgumentException("schema_required");
        return (Map<String, Object>) schema;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> schemaFromDataBody(final Map<String, Object> body) {
        final Object data = body.get("data");
        if (!(data instanceof Map<?, ?>)) throw new IllegalArgumentException("data_required");
        return (Map<String, Object>) data;
    }

    private String extractToken(final JettyHttpExchange jx) {
        final String auth = jx.request().getHeaders().get("Authorization");
        return (auth != null && auth.startsWith("Bearer ")) ? auth.substring(7) : null;
    }

    /** Exige rol organizer/admin Y que el caller sea dueño real de la conferencia (o admin
     *  plataforma). Envía la respuesta de error y devuelve null si no cumple. */
    private UsersPort.ValidationResult requireConferenceOwner(final JettyHttpExchange jx, final String conferenceId) {
        final String token = extractToken(jx);
        if (token == null) { sendError(jx, 401, "token_missing", "Authorization required"); return null; }
        final var v = usersPort.validate(token);
        if (!v.valid() || !isOrganizerOrAdmin(v.role())) {
            sendError(jx, 403, "forbidden", "Only organizers can manage survey questions");
            return null;
        }
        final boolean platformAdmin = legacyRoleHasAny(v.role(), "admin");
        if (!platformAdmin && !usersPort.isConferenceOwner(conferenceId, v.subjectUuid())) {
            sendError(jx, 403, "forbidden", "You are not the organizer of this conference");
            return null;
        }
        return v;
    }

    /** Exige MANAGE_SURVEY en el evento, o ownership/admin para compatibilidad. */
    private UsersPort.ValidationResult requireSurveyManager(final JettyHttpExchange jx, final String conferenceId) {
        final String token = extractToken(jx);
        if (token == null) { sendError(jx, 401, "token_missing", "Authorization required"); return null; }
        final var v = usersPort.validate(token);
        if (!v.valid()) { sendError(jx, 401, "token_invalid", "Invalid token"); return null; }
        if (!usersPort.hasSurveyManagementAccess(conferenceId, token)) {
            sendError(jx, 403, "forbidden", "No tienes permiso para gestionar la encuesta");
            return null;
        }
        return v;
    }
}
