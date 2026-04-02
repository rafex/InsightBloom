package dev.rafex.insightbloom.moderation.adapters.inbound.http.handlers;
import dev.rafex.insightbloom.moderation.application.usecases.*;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Callback;
import java.util.Map;
public class ModerationWordHandler extends BaseHandler {
    private final ListModerationUseCase listUseCase;
    private final CensorWordUseCase censorUseCase;
    private final RestoreWordUseCase restoreUseCase;
    public ModerationWordHandler(ListModerationUseCase listUseCase, CensorWordUseCase censorUseCase, RestoreWordUseCase restoreUseCase) {
        this.listUseCase = listUseCase; this.censorUseCase = censorUseCase; this.restoreUseCase = restoreUseCase;
    }
    @Override
    public boolean handle(Request request, Response response, Callback callback) throws Exception {
        String path = request.getHttpURI().getPath();
        String method = request.getMethod();
        // GET /api/v1/conferences/{conferenceId}/moderation/words
        if ("GET".equals(method) && path.contains("/moderation/words")) {
            String[] parts = path.split("/");
            String conferenceId = null;
            for (int i = 0; i < parts.length; i++) {
                if ("conferences".equals(parts[i]) && i+1 < parts.length) { conferenceId = parts[i+1]; break; }
            }
            int page = queryParam(request, "page", 1);
            int pageSize = queryParam(request, "pageSize", 50);
            var result = listUseCase.listWords(conferenceId, page, pageSize);
            okPaged(response, callback, result.items(), result.page(), result.pageSize(), result.total());
            return true;
        }
        // POST /api/v1/moderation/words/{wordId}/censor
        if ("POST".equals(method) && path.contains("/censor") && path.contains("/words/")) {
            String[] parts = path.split("/");
            String wordId = null;
            for (int i = 0; i < parts.length; i++) {
                if ("words".equals(parts[i]) && i+1 < parts.length) { wordId = parts[i+1]; break; }
            }
            var body = readBody(request, Map.class);
            try {
                censorUseCase.execute(new CensorWordUseCase.Request(wordId, (String) body.get("reason"), (String) body.get("updatedByUserUuid")));
                ok(response, callback, Map.of("status", "censored"));
            } catch (IllegalArgumentException e) { error(response, callback, 404, e.getMessage(), "Word not found"); }
            return true;
        }
        // POST /api/v1/moderation/words/{wordId}/restore
        if ("POST".equals(method) && path.contains("/restore") && path.contains("/words/")) {
            String[] parts = path.split("/");
            String wordId = null;
            for (int i = 0; i < parts.length; i++) {
                if ("words".equals(parts[i]) && i+1 < parts.length) { wordId = parts[i+1]; break; }
            }
            var body = readBody(request, Map.class);
            try {
                restoreUseCase.execute(wordId, (String) body.getOrDefault("updatedByUserUuid", "system"));
                ok(response, callback, Map.of("status", "restored"));
            } catch (IllegalArgumentException e) { error(response, callback, 404, e.getMessage(), "Word not found"); }
            return true;
        }
        error(response, callback, 404, "not_found", "Endpoint not found");
        return true;
    }
}
