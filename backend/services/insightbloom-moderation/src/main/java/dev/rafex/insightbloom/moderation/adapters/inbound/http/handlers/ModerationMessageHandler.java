package dev.rafex.insightbloom.moderation.adapters.inbound.http.handlers;
import dev.rafex.insightbloom.moderation.application.usecases.*;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Callback;
import java.util.Map;
public class ModerationMessageHandler extends BaseHandler {
    private final ListModerationUseCase listUseCase;
    private final CensorMessageUseCase censorUseCase;
    private final RestoreMessageUseCase restoreUseCase;
    private final EditMessageUseCase editUseCase;
    public ModerationMessageHandler(ListModerationUseCase listUseCase, CensorMessageUseCase censorUseCase,
                                     RestoreMessageUseCase restoreUseCase, EditMessageUseCase editUseCase) {
        this.listUseCase = listUseCase; this.censorUseCase = censorUseCase;
        this.restoreUseCase = restoreUseCase; this.editUseCase = editUseCase;
    }
    @Override
    public boolean handle(Request request, Response response, Callback callback) throws Exception {
        String path = request.getHttpURI().getPath();
        String method = request.getMethod();
        if ("GET".equals(method) && path.contains("/moderation/messages")) {
            String conferenceId = extractSegmentAfter(path, "conferences");
            int page = queryParam(request, "page", 1);
            int pageSize = queryParam(request, "pageSize", 50);
            String status = queryString(request, "status");
            var result = listUseCase.listMessages(conferenceId, status, page, pageSize);
            okPaged(response, callback, result.items(), result.page(), result.pageSize(), result.total());
            return true;
        }
        if ("POST".equals(method) && path.contains("/censor") && path.contains("/messages/")) {
            String msgId = extractSegmentAfter(path, "messages");
            var body = readBody(request, Map.class);
            try {
                censorUseCase.execute(new CensorMessageUseCase.Request(
                    msgId,
                    (String) body.getOrDefault("target", "detail"),
                    (String) body.get("reason"),
                    (String) body.get("updatedByUserUuid"),
                    (String) body.get("conferenceUuid"),
                    (String) body.get("wordText"),
                    (String) body.get("detailText")
                ));
                ok(response, callback, Map.of("status", "censored"));
            } catch (IllegalArgumentException e) { error(response, callback, 404, e.getMessage(), "Message not found"); }
            return true;
        }
        if ("POST".equals(method) && path.contains("/restore") && path.contains("/messages/")) {
            String msgId = extractSegmentAfter(path, "messages");
            var body = readBody(request, Map.class);
            try {
                restoreUseCase.execute(msgId, (String) body.getOrDefault("updatedByUserUuid","system"));
                ok(response, callback, Map.of("status","restored"));
            } catch (IllegalArgumentException e) { error(response, callback, 404, e.getMessage(), "Message not found"); }
            return true;
        }
        // PATCH /api/v1/moderation/messages/{messageId}
        if ("PATCH".equals(method) && path.contains("/messages/")) {
            String msgId = extractSegmentAfter(path, "messages");
            var body = readBody(request, Map.class);
            try {
                editUseCase.execute(new EditMessageUseCase.Request(msgId, (String) body.get("editedWord"), (String) body.get("editedDetail"), (String) body.get("updatedByUserUuid")));
                ok(response, callback, Map.of("status", "updated"));
            } catch (IllegalArgumentException e) { error(response, callback, 404, e.getMessage(), "Message not found"); }
            return true;
        }
        error(response, callback, 404, "not_found", "Endpoint not found");
        return true;
    }
    private String extractSegmentAfter(String path, String key) {
        String[] parts = path.split("/");
        for (int i = 0; i < parts.length; i++) {
            if (key.equals(parts[i]) && i+1 < parts.length) return parts[i+1];
        }
        return null;
    }
}
