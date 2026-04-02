package dev.rafex.insightbloom.ingest.adapters.inbound.http.handlers;
import dev.rafex.insightbloom.ingest.application.usecases.GetMessageUseCase;
import dev.rafex.insightbloom.ingest.application.usecases.IngestMessageUseCase;
import dev.rafex.insightbloom.ingest.domain.ports.UsersPort;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Callback;
import java.util.Map;
public class IngestHandler extends BaseHandler {
    private final IngestMessageUseCase ingestUseCase;
    private final GetMessageUseCase getMessageUseCase;
    private final UsersPort usersPort;
    public IngestHandler(IngestMessageUseCase ingestUseCase, GetMessageUseCase getMessageUseCase, UsersPort usersPort) {
        this.ingestUseCase=ingestUseCase; this.getMessageUseCase=getMessageUseCase; this.usersPort=usersPort;
    }
    @Override
    public boolean handle(Request req, Response res, Callback cb) throws Exception {
        String path = req.getHttpURI().getPath();
        String method = req.getMethod();
        // POST /api/v1/messages
        if ("POST".equals(method) && path.matches(".*/messages/?$")) {
            return handleIngest(req, res, cb, false);
        }
        // POST /api/v1/webhooks/messages
        if ("POST".equals(method) && path.contains("/webhooks/")) {
            return handleIngest(req, res, cb, true);
        }
        // GET /api/v1/messages/{id}
        if ("GET".equals(method) && path.matches(".*/messages/[^/]+$")) {
            String id = path.substring(path.lastIndexOf("/")+1);
            return handleGet(id, res, cb);
        }
        error(res,cb,404,"not_found","Endpoint not found");
        return true;
    }
    private boolean handleIngest(Request req, Response res, Callback cb, boolean isWebhook) throws Exception {
        String token = getToken(req);
        String authorUuid = "anonymous"; String authorKind = "guest"; String role = "guest";
        String displayName = null;
        if (token != null) {
            var validation = usersPort.validate(token);
            if (!validation.valid()) { error(res,cb,401,"token_invalid","Invalid token"); return true; }
            authorUuid = validation.subjectUuid(); authorKind = validation.kind(); role = validation.role();
        }
        var body = readBody(req, Map.class);
        @SuppressWarnings("unchecked")
        Map<Object,Object> author = body.get("author") instanceof Map ? (Map<Object,Object>) body.get("author") : new java.util.HashMap<>();
        @SuppressWarnings("unchecked")
        Map<Object,Object> device = body.get("device") instanceof Map ? (Map<Object,Object>) body.get("device") : new java.util.HashMap<>();
        @SuppressWarnings("unchecked")
        Map<Object,Object> msgMap = body.get("message") instanceof Map ? (Map<Object,Object>) body.get("message") : (Map<Object,Object>)(Map<?,?>) body;
        String conferenceUuid = (String) body.get("conferenceId");
        if (author.get("displayName") != null) displayName = (String) author.get("displayName");
        if (author.get("userId") != null) authorUuid = (String) author.get("userId");
        if (author.get("kind") != null) authorKind = (String) author.get("kind");
        String fingerprint = device.get("fingerprint") != null ? (String) device.get("fingerprint") : "";
        String msgType = msgMap.get("type") != null ? (String) msgMap.get("type") : "doubt";
        String word = msgMap.get("word") != null ? (String) msgMap.get("word") : "";
        String detail = msgMap.get("detail") != null ? (String) msgMap.get("detail") : "";
        String receivedAt = body.get("receivedAt") != null ? (String) body.get("receivedAt") : java.time.Instant.now().toString();
        var result = ingestUseCase.execute(new IngestMessageUseCase.IngestRequest(
            conferenceUuid, authorUuid, authorKind, displayName, fingerprint,
            msgType, isWebhook ? "WEBHOOK" : "REST", word, detail, receivedAt
        ));
        created(res, cb, Map.of("messageId", result.getUuid(), "status", result.getWordStatus().name().toLowerCase()));
        return true;
    }
    private boolean handleGet(String id, Response res, Callback cb) throws Exception {
        getMessageUseCase.execute(id)
            .ifPresentOrElse(
                m -> { try { ok(res,cb,m); } catch (Exception e) { throw new RuntimeException(e); } },
                () -> { try { error(res,cb,404,"message_not_found","Message not found"); } catch (Exception e) { throw new RuntimeException(e); } }
            );
        return true;
    }
}
