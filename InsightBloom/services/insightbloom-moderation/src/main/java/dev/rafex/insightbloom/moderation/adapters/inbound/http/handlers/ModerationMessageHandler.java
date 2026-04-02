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
    public ModerationMessageHandler(ListModerationUseCase listUseCase, CensorMessageUseCase censorUseCase, RestoreMessageUseCase restoreUseCase) {
        this.listUseCase = listUseCase; this.censorUseCase = censorUseCase; this.restoreUseCase = restoreUseCase;
    }
    @Override
    public boolean handle(Request request, Response response, Callback callback) throws Exception {
        String path = request.getHttpURI().getPath();
        String method = request.getMethod();
        if ("GET".equals(method) && path.contains("/moderation/messages")) {
            String[] parts = path.split("/");
            String conferenceId = null;
            for (int i = 0; i < parts.length; i++) {
                if ("conferences".equals(parts[i]) && i+1 < parts.length) { conferenceId = parts[i+1]; break; }
            }
            int page = queryParam(request, "page", 1);
            int pageSize = queryParam(request, "pageSize", 50);
            var result = listUseCase.listMessages(conferenceId, page, pageSize);
            okPaged(response, callback, result.items(), result.page(), result.pageSize(), result.total());
            return true;
        }
        if ("POST".equals(method) && path.contains("/censor") && path.contains("/messages/")) {
            String[] parts = path.split("/");
            String msgId = null;
            for (int i = 0; i < parts.length; i++) {
                if ("messages".equals(parts[i]) && i+1 < parts.length) { msgId = parts[i+1]; break; }
            }
            var body = readBody(request, Map.class);
            try {
                censorUseCase.execute(new CensorMessageUseCase.Request(msgId, (String) body.getOrDefault("target","detail"), (String) body.get("reason"), (String) body.get("updatedByUserUuid")));
                ok(response, callback, Map.of("status", "censored"));
            } catch (IllegalArgumentException e) { error(response, callback, 404, e.getMessage(), "Message not found"); }
            return true;
        }
        if ("POST".equals(method) && path.contains("/restore") && path.contains("/messages/")) {
            String[] parts = path.split("/");
            String msgId = null;
            for (int i = 0; i < parts.length; i++) {
                if ("messages".equals(parts[i]) && i+1 < parts.length) { msgId = parts[i+1]; break; }
            }
            var body = readBody(request, Map.class);
            try {
                restoreUseCase.execute(msgId, (String) body.getOrDefault("updatedByUserUuid","system"));
                ok(response, callback, Map.of("status","restored"));
            } catch (IllegalArgumentException e) { error(response, callback, 404, e.getMessage(), "Message not found"); }
            return true;
        }
        error(response, callback, 404, "not_found", "Endpoint not found");
        return true;
    }
}
