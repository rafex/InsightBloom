package dev.rafex.insightbloom.moderation.adapters.inbound.http.handlers;

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
import dev.rafex.insightbloom.moderation.application.usecases.*;
import org.eclipse.jetty.server.Request;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class ConferenceModerationHandler extends NonBlockingResourceHandler {

    private static final JsonCodec JSON_CODEC = JsonUtils.codec();
    private static final JettyApiResponses RESPONSES = new JettyApiResponses(JSON_CODEC);

    private final ListModerationUseCase listUseCase;
    private final CensorWordUseCase censorWordUseCase;
    private final RestoreWordUseCase restoreWordUseCase;
    private final EditWordUseCase editWordUseCase;
    private final DeleteWordUseCase deleteWordUseCase;
    private final CensorMessageUseCase censorMessageUseCase;
    private final RestoreMessageUseCase restoreMessageUseCase;
    private final EditMessageUseCase editMessageUseCase;
    private final DeleteMessageUseCase deleteMessageUseCase;

    public ConferenceModerationHandler(final ListModerationUseCase listUseCase,
                                       final CensorWordUseCase censorWordUseCase,
                                       final RestoreWordUseCase restoreWordUseCase,
                                       final EditWordUseCase editWordUseCase,
                                       final DeleteWordUseCase deleteWordUseCase,
                                       final CensorMessageUseCase censorMessageUseCase,
                                       final RestoreMessageUseCase restoreMessageUseCase,
                                       final EditMessageUseCase editMessageUseCase,
                                       final DeleteMessageUseCase deleteMessageUseCase) {
        super(JSON_CODEC);
        this.listUseCase = listUseCase;
        this.censorWordUseCase = censorWordUseCase;
        this.restoreWordUseCase = restoreWordUseCase;
        this.editWordUseCase = editWordUseCase;
        this.deleteWordUseCase = deleteWordUseCase;
        this.censorMessageUseCase = censorMessageUseCase;
        this.restoreMessageUseCase = restoreMessageUseCase;
        this.editMessageUseCase = editMessageUseCase;
        this.deleteMessageUseCase = deleteMessageUseCase;
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
                Route.of("/{conferenceId}/moderation/messages/{msgId}", Set.of("PATCH")));
    }

    @Override
    public Set<String> supportedMethods() {
        return Set.of("GET", "POST", "PATCH");
    }

    @Override
    public boolean get(final HttpExchange x) {
        final var jx = asJetty(x);
        final String conferenceId = jx.pathParam("conferenceId");
        final String path = jx.path();
        try {
            if (path.contains("/moderation/words")) {
                return handleListWords(jx, conferenceId);
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
        try {
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
        RESPONSES.json(jx.response(), jx.callback(), 200,
                new ApiResponse<>(result.items(), ApiMeta.paged(UUID.randomUUID().toString(), result.page(), result.pageSize(), result.total())));
        return true;
    }

    private boolean handleListMessages(final JettyHttpExchange jx, final String conferenceId) throws Exception {
        final String status = queryParam(jx, "status");
        final int page = parseIntParam(queryParam(jx, "page"), 1);
        final int pageSize = parseIntParam(queryParam(jx, "pageSize"), 50);
        final var result = listUseCase.listMessages(conferenceId, status, page, pageSize);
        RESPONSES.json(jx.response(), jx.callback(), 200,
                new ApiResponse<>(result.items(), ApiMeta.paged(UUID.randomUUID().toString(), result.page(), result.pageSize(), result.total())));
        return true;
    }

    private boolean handleCensorWord(final JettyHttpExchange jx, final String wordId) throws Exception {
        final var body = JSON_CODEC.readValue(Request.asInputStream(jx.request()), Map.class);
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
        final var body = JSON_CODEC.readValue(Request.asInputStream(jx.request()), Map.class);
        try {
            restoreWordUseCase.execute(wordId, (String) body.getOrDefault("updatedByUserUuid", "system"));
            sendOk(jx, Map.of("status", "restored"));
        } catch (final IllegalArgumentException e) {
            sendError(jx, 404, e.getMessage(), "Word not found");
        }
        return true;
    }

    private boolean handleEditWord(final JettyHttpExchange jx, final String wordId) throws Exception {
        final var body = JSON_CODEC.readValue(Request.asInputStream(jx.request()), Map.class);
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
        final var body = JSON_CODEC.readValue(Request.asInputStream(jx.request()), Map.class);
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
        final var body = JSON_CODEC.readValue(Request.asInputStream(jx.request()), Map.class);
        try {
            restoreMessageUseCase.execute(msgId, (String) body.getOrDefault("updatedByUserUuid", "system"));
            sendOk(jx, Map.of("status", "restored"));
        } catch (final IllegalArgumentException e) {
            sendError(jx, 404, e.getMessage(), "Message not found");
        }
        return true;
    }

    private boolean handleEditMessage(final JettyHttpExchange jx, final String msgId) throws Exception {
        final var body = JSON_CODEC.readValue(Request.asInputStream(jx.request()), Map.class);
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
        final var body = JSON_CODEC.readValue(Request.asInputStream(jx.request()), Map.class);
        try {
            deleteWordUseCase.execute(new DeleteWordUseCase.Request(
                    wordId, (String) body.getOrDefault("deletedByUserUuid", "system")));
            sendOk(jx, Map.of("status", "deleted"));
        } catch (final IllegalArgumentException e) {
            sendError(jx, 404, e.getMessage(), "Word not found");
        }
        return true;
    }

    private boolean handleDeleteMessage(final JettyHttpExchange jx, final String msgId) throws Exception {
        final var body = JSON_CODEC.readValue(Request.asInputStream(jx.request()), Map.class);
        try {
            deleteMessageUseCase.execute(new DeleteMessageUseCase.Request(
                    msgId, (String) body.getOrDefault("deletedByUserUuid", "system")));
            sendOk(jx, Map.of("status", "deleted"));
        } catch (final IllegalArgumentException e) {
            sendError(jx, 404, e.getMessage(), "Message not found");
        }
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

    private static int parseIntParam(final String value, final int defaultValue) {
        if (value == null) return defaultValue;
        try { return Integer.parseInt(value); } catch (final NumberFormatException e) { return defaultValue; }
    }

    private static JettyHttpExchange asJetty(final HttpExchange x) {
        return (JettyHttpExchange) x;
    }
}
