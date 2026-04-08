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
    private final EditWordUseCase editUseCase;
    public ModerationWordHandler(ListModerationUseCase listUseCase, CensorWordUseCase censorUseCase,
                                  RestoreWordUseCase restoreUseCase, EditWordUseCase editUseCase) {
        this.listUseCase = listUseCase; this.censorUseCase = censorUseCase;
        this.restoreUseCase = restoreUseCase; this.editUseCase = editUseCase;
    }
    @Override
    public boolean handle(Request request, Response response, Callback callback) throws Exception {
        String path = request.getHttpURI().getPath();
        String method = request.getMethod();
        // GET /api/v1/conferences/{conferenceId}/moderation/words
        if ("GET".equals(method) && path.contains("/moderation/words")) {
            String conferenceId = extractSegmentAfter(path, "conferences");
            int page = queryParam(request, "page", 1);
            int pageSize = queryParam(request, "pageSize", 50);
            String status = queryString(request, "status");
            var result = listUseCase.listWords(conferenceId, status, page, pageSize);
            okPaged(response, callback, result.items(), result.page(), result.pageSize(), result.total());
            return true;
        }
        // POST /api/v1/moderation/words/{wordId}/censor
        if ("POST".equals(method) && path.contains("/censor") && path.contains("/words/")) {
            String wordId = extractSegmentAfter(path, "words");
            var body = readBody(request, Map.class);
            try {
                censorUseCase.execute(new CensorWordUseCase.Request(wordId, (String) body.get("reason"), (String) body.get("updatedByUserUuid")));
                ok(response, callback, Map.of("status", "censored"));
            } catch (IllegalArgumentException e) { error(response, callback, 404, e.getMessage(), "Word not found"); }
            return true;
        }
        // POST /api/v1/moderation/words/{wordId}/restore
        if ("POST".equals(method) && path.contains("/restore") && path.contains("/words/")) {
            String wordId = extractSegmentAfter(path, "words");
            var body = readBody(request, Map.class);
            try {
                restoreUseCase.execute(wordId, (String) body.getOrDefault("updatedByUserUuid", "system"));
                ok(response, callback, Map.of("status", "restored"));
            } catch (IllegalArgumentException e) { error(response, callback, 404, e.getMessage(), "Word not found"); }
            return true;
        }
        // PATCH /api/v1/moderation/words/{wordId}
        if ("PATCH".equals(method) && path.contains("/words/")) {
            String wordId = extractSegmentAfter(path, "words");
            var body = readBody(request, Map.class);
            try {
                editUseCase.execute(new EditWordUseCase.Request(wordId, (String) body.get("value"), (String) body.get("updatedByUserUuid")));
                ok(response, callback, Map.of("status", "updated"));
            } catch (IllegalArgumentException e) { error(response, callback, 404, e.getMessage(), "Word not found"); }
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
