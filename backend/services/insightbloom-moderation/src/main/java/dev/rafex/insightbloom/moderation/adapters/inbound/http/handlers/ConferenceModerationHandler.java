package dev.rafex.insightbloom.moderation.adapters.inbound.http.handlers;

import dev.rafex.ether.http.core.HttpExchange;
import dev.rafex.ether.http.core.Route;
import dev.rafex.ether.http.jetty12.exchange.JettyHttpExchange;
import dev.rafex.insightbloom.common.http.BaseResourceHandler;
import dev.rafex.insightbloom.contracts.ApiMeta;
import dev.rafex.insightbloom.moderation.application.usecases.*;
import dev.rafex.insightbloom.moderation.domain.ports.UsersPort;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class ConferenceModerationHandler extends BaseResourceHandler {

    private final ListModerationUseCase listUseCase;
    private final CensorWordUseCase censorWordUseCase;
    private final RestoreWordUseCase restoreWordUseCase;
    private final EditWordUseCase editWordUseCase;
    private final DeleteWordUseCase deleteWordUseCase;
    private final CensorMessageUseCase censorMessageUseCase;
    private final RestoreMessageUseCase restoreMessageUseCase;
    private final EditMessageUseCase editMessageUseCase;
    private final DeleteMessageUseCase deleteMessageUseCase;
    private final DeleteConferenceDataUseCase deleteConferenceDataUseCase;
    private final AnswerMessageUseCase answerMessageUseCase;
    private final GetMessageAnswerUseCase getMessageAnswerUseCase;
    private final UsersPort usersPort;

    public ConferenceModerationHandler(final ListModerationUseCase listUseCase,
                                       final CensorWordUseCase censorWordUseCase,
                                       final RestoreWordUseCase restoreWordUseCase,
                                       final EditWordUseCase editWordUseCase,
                                       final DeleteWordUseCase deleteWordUseCase,
                                       final CensorMessageUseCase censorMessageUseCase,
                                       final RestoreMessageUseCase restoreMessageUseCase,
                                       final EditMessageUseCase editMessageUseCase,
                                       final DeleteMessageUseCase deleteMessageUseCase,
                                       final DeleteConferenceDataUseCase deleteConferenceDataUseCase,
                                       final AnswerMessageUseCase answerMessageUseCase,
                                       final GetMessageAnswerUseCase getMessageAnswerUseCase,
                                       final UsersPort usersPort) {
        this.listUseCase = listUseCase;
        this.censorWordUseCase = censorWordUseCase;
        this.restoreWordUseCase = restoreWordUseCase;
        this.editWordUseCase = editWordUseCase;
        this.deleteWordUseCase = deleteWordUseCase;
        this.censorMessageUseCase = censorMessageUseCase;
        this.restoreMessageUseCase = restoreMessageUseCase;
        this.editMessageUseCase = editMessageUseCase;
        this.deleteMessageUseCase = deleteMessageUseCase;
        this.deleteConferenceDataUseCase = deleteConferenceDataUseCase;
        this.answerMessageUseCase = answerMessageUseCase;
        this.getMessageAnswerUseCase = getMessageAnswerUseCase;
        this.usersPort = usersPort;
    }

    /** Pública por diseño: la usa la vista de timeline del portal de asistentes para mostrar respuestas. */
    private static boolean isPublicGet(final String path) {
        return path.contains("/moderation/messages/") && path.endsWith("/answer");
    }

    private boolean requireOrganizer(final JettyHttpExchange jx) {
        final String authHeader = jx.request().getHeaders().get("Authorization");
        final String token = (authHeader != null && authHeader.startsWith("Bearer ")) ? authHeader.substring(7) : null;
        if (token == null) {
            sendError(jx, 401, "unauthorized", "Missing bearer token");
            return false;
        }
        final var validation = usersPort.validate(token);
        if (!validation.valid() || !isOrganizerOrAdmin(validation.role())) {
            sendError(jx, 403, "forbidden", "Only organizers can moderate");
            return false;
        }
        return true;
    }

    private static boolean isOrganizerOrAdmin(final String role) {
        return role != null && (role.contains("organizer") || role.contains("admin"));
    }

    @Override
    protected String basePath() {
        return "/api/v1/conferences";
    }

    @Override
    protected List<Route> routes() {
        return List.of(
                Route.of("/{conferenceId}/moderation/words", Set.of("GET")),
                Route.of("/{conferenceId}/moderation/words/{wordId}/censor", Set.of("POST")),
                Route.of("/{conferenceId}/moderation/words/{wordId}/restore", Set.of("POST")),
                Route.of("/{conferenceId}/moderation/words/{wordId}/delete", Set.of("POST")),
                Route.of("/{conferenceId}/moderation/words/{wordId}", Set.of("PATCH")),
                Route.of("/{conferenceId}/moderation/messages", Set.of("GET")),
                Route.of("/{conferenceId}/moderation/messages/{msgId}/censor", Set.of("POST")),
                Route.of("/{conferenceId}/moderation/messages/{msgId}/restore", Set.of("POST")),
                Route.of("/{conferenceId}/moderation/messages/{msgId}/delete", Set.of("POST")),
                Route.of("/{conferenceId}/moderation/messages/{msgId}/answer", Set.of("GET", "POST")),
                Route.of("/{conferenceId}/moderation/messages/{msgId}", Set.of("PATCH")),
                Route.of("/{conferenceId}/moderation", Set.of("DELETE")));
    }

    @Override
    public Set<String> supportedMethods() {
        return Set.of("GET", "POST", "PATCH", "DELETE");
    }

    @Override
    public boolean delete(final HttpExchange x) {
        final var jx = asJetty(x);
        if (!validInternalAuth(jx)) { sendError(jx, 403, "forbidden", "Internal access only"); return true; }
        try {
            deleteConferenceDataUseCase.execute(jx.pathParam("conferenceId"));
            sendOk(jx, Map.of("status", "deleted"));
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", "Internal error");
        }
        return true;
    }

    @Override
    public boolean get(final HttpExchange x) {
        final var jx = asJetty(x);
        final String conferenceId = jx.pathParam("conferenceId");
        final String path = jx.path();
        if (!isPublicGet(path) && !requireOrganizer(jx)) {
            return true;
        }
        try {
            if (path.contains("/moderation/words")) {
                return handleListWords(jx, conferenceId);
            }
            if (path.contains("/moderation/messages/") && path.endsWith("/answer")) {
                return handleGetAnswer(jx, jx.pathParam("msgId"));
            }
            if (path.contains("/moderation/messages")) {
                return handleListMessages(jx, conferenceId);
            }
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
        }
        sendError(jx, 404, "not_found", "Endpoint not found");
        return true;
    }

    @Override
    public boolean post(final HttpExchange x) {
        final var jx = asJetty(x);
        final String path = jx.path();
        if (!requireOrganizer(jx)) {
            return true;
        }
        try {
            if (path.contains("/moderation/messages/") && path.endsWith("/answer")) {
                return handleAnswerMessage(jx, jx.pathParam("msgId"));
            }
            if (path.contains("/moderation/words/") && path.endsWith("/censor")) {
                return handleCensorWord(jx, jx.pathParam("wordId"));
            }
            if (path.contains("/moderation/words/") && path.endsWith("/restore")) {
                return handleRestoreWord(jx, jx.pathParam("wordId"));
            }
            if (path.contains("/moderation/words/") && path.endsWith("/delete")) {
                return handleDeleteWord(jx, jx.pathParam("wordId"));
            }
            if (path.contains("/moderation/messages/") && path.endsWith("/censor")) {
                return handleCensorMessage(jx, jx.pathParam("msgId"));
            }
            if (path.contains("/moderation/messages/") && path.endsWith("/restore")) {
                return handleRestoreMessage(jx, jx.pathParam("msgId"));
            }
            if (path.contains("/moderation/messages/") && path.endsWith("/delete")) {
                return handleDeleteMessage(jx, jx.pathParam("msgId"));
            }
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
            return true;
        }
        sendError(jx, 404, "not_found", "Endpoint not found");
        return true;
    }

    @Override
    public boolean patch(final HttpExchange x) {
        final var jx = asJetty(x);
        final String path = jx.path();
        if (!requireOrganizer(jx)) {
            return true;
        }
        try {
            if (path.contains("/moderation/words/")) {
                return handleEditWord(jx, jx.pathParam("wordId"));
            }
            if (path.contains("/moderation/messages/")) {
                return handleEditMessage(jx, jx.pathParam("msgId"));
            }
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
            return true;
        }
        sendError(jx, 404, "not_found", "Endpoint not found");
        return true;
    }

    private boolean handleListWords(final JettyHttpExchange jx, final String conferenceId) throws Exception {
        final String status = queryParam(jx, "status");
        final int page = parseIntParam(queryParam(jx, "page"), 1);
        final int pageSize = parseIntParam(queryParam(jx, "pageSize"), 50);
        final var result = listUseCase.listWords(conferenceId, status, page, pageSize);
        sendOk(jx, 200, result.items(),
                ApiMeta.paged(java.util.UUID.randomUUID().toString(), result.page(), result.pageSize(), result.total()));
        return true;
    }

    private boolean handleListMessages(final JettyHttpExchange jx, final String conferenceId) throws Exception {
        final String status = queryParam(jx, "status");
        final int page = parseIntParam(queryParam(jx, "page"), 1);
        final int pageSize = parseIntParam(queryParam(jx, "pageSize"), 50);
        final var result = listUseCase.listMessages(conferenceId, status, page, pageSize);
        sendOk(jx, 200, result.items(),
                ApiMeta.paged(java.util.UUID.randomUUID().toString(), result.page(), result.pageSize(), result.total()));
        return true;
    }

    private boolean handleCensorWord(final JettyHttpExchange jx, final String wordId) throws Exception {
        final var body = parseBody(jx);
        try {
            censorWordUseCase.execute(new CensorWordUseCase.Request(
                    wordId, (String) body.get("reason"), (String) body.get("updatedByUserUuid")));
            sendOk(jx, Map.of("status", "censored"));
        } catch (final IllegalArgumentException e) {
            sendError(jx, 404, e.getMessage(), "Word not found");
        }
        return true;
    }

    private boolean handleRestoreWord(final JettyHttpExchange jx, final String wordId) throws Exception {
        final var body = parseBody(jx);
        try {
            restoreWordUseCase.execute(wordId, (String) body.getOrDefault("updatedByUserUuid", "system"));
            sendOk(jx, Map.of("status", "restored"));
        } catch (final IllegalArgumentException e) {
            sendError(jx, 404, e.getMessage(), "Word not found");
        }
        return true;
    }

    private boolean handleEditWord(final JettyHttpExchange jx, final String wordId) throws Exception {
        final var body = parseBody(jx);
        try {
            editWordUseCase.execute(new EditWordUseCase.Request(
                    wordId, (String) body.get("value"), (String) body.get("updatedByUserUuid")));
            sendOk(jx, Map.of("status", "updated"));
        } catch (final IllegalArgumentException e) {
            sendError(jx, 404, e.getMessage(), "Word not found");
        }
        return true;
    }

    private boolean handleCensorMessage(final JettyHttpExchange jx, final String msgId) throws Exception {
        final var body = parseBody(jx);
        try {
            censorMessageUseCase.execute(new CensorMessageUseCase.Request(
                    msgId,
                    (String) body.getOrDefault("target", "detail"),
                    (String) body.get("reason"),
                    (String) body.get("updatedByUserUuid"),
                    (String) body.get("conferenceUuid"),
                    (String) body.get("wordText"),
                    (String) body.get("detailText")));
            sendOk(jx, Map.of("status", "censored"));
        } catch (final IllegalArgumentException e) {
            sendError(jx, 404, e.getMessage(), "Message not found");
        }
        return true;
    }

    private boolean handleRestoreMessage(final JettyHttpExchange jx, final String msgId) throws Exception {
        final var body = parseBody(jx);
        try {
            restoreMessageUseCase.execute(msgId, (String) body.getOrDefault("updatedByUserUuid", "system"));
            sendOk(jx, Map.of("status", "restored"));
        } catch (final IllegalArgumentException e) {
            sendError(jx, 404, e.getMessage(), "Message not found");
        }
        return true;
    }

    private boolean handleEditMessage(final JettyHttpExchange jx, final String msgId) throws Exception {
        final var body = parseBody(jx);
        try {
            editMessageUseCase.execute(new EditMessageUseCase.Request(
                    msgId, (String) body.get("editedWord"), (String) body.get("editedDetail"),
                    (String) body.get("updatedByUserUuid")));
            sendOk(jx, Map.of("status", "updated"));
        } catch (final IllegalArgumentException e) {
            sendError(jx, 404, e.getMessage(), "Message not found");
        }
        return true;
    }

    private boolean handleDeleteWord(final JettyHttpExchange jx, final String wordId) throws Exception {
        final var body = parseBody(jx);
        try {
            deleteWordUseCase.execute(new DeleteWordUseCase.Request(
                    wordId, (String) body.getOrDefault("deletedByUserUuid", "system")));
            sendOk(jx, Map.of("status", "deleted"));
        } catch (final IllegalArgumentException e) {
            sendError(jx, 404, e.getMessage(), "Word not found");
        }
        return true;
    }

    private boolean handleAnswerMessage(final JettyHttpExchange jx, final String msgId) throws Exception {
        final var body = parseBody(jx);
        try {
            answerMessageUseCase.execute(new AnswerMessageUseCase.Request(
                    msgId, (String) body.get("answerText"), (String) body.get("answeredByUserUuid")));
            sendOk(jx, Map.of("status", "answered"));
        } catch (final IllegalArgumentException e) {
            sendError(jx, 404, e.getMessage(), "Message not found");
        }
        return true;
    }

    private boolean handleGetAnswer(final JettyHttpExchange jx, final String msgId) throws Exception {
        final var result = getMessageAnswerUseCase.execute(msgId);
        if (result.isPresent()) {
            sendOk(jx, result.get());
        } else {
            sendError(jx, 404, "not_found", "No answer found for this message");
        }
        return true;
    }

    private boolean handleDeleteMessage(final JettyHttpExchange jx, final String msgId) throws Exception {
        final var body = parseBody(jx);
        try {
            deleteMessageUseCase.execute(new DeleteMessageUseCase.Request(
                    msgId, (String) body.getOrDefault("deletedByUserUuid", "system")));
            sendOk(jx, Map.of("status", "deleted"));
        } catch (final IllegalArgumentException e) {
            sendError(jx, 404, e.getMessage(), "Message not found");
        }
        return true;
    }

    private static int parseIntParam(final String value, final int defaultValue) {
        if (value == null) return defaultValue;
        try { return Integer.parseInt(value); } catch (final NumberFormatException e) { return defaultValue; }
    }

}
