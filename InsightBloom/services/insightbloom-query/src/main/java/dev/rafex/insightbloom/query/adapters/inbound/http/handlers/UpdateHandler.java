package dev.rafex.insightbloom.query.adapters.inbound.http.handlers;
import dev.rafex.insightbloom.query.application.usecases.UpdateCloudUseCase;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Callback;
import java.util.Map;
public class UpdateHandler extends BaseHandler {
    private final UpdateCloudUseCase useCase;
    public UpdateHandler(UpdateCloudUseCase useCase) { this.useCase = useCase; }
    @Override
    public boolean handle(Request req, Response res, Callback cb) throws Exception {
        if (!"POST".equals(req.getMethod())) { error(res,cb,405,"method_not_allowed","POST required"); return true; }
        var body = readBody(req, Map.class);
        useCase.execute(new UpdateCloudUseCase.UpdateRequest(
            (String) body.get("conferenceUuid"), (String) body.get("wordNormalized"),
            (String) body.get("wordCanonical"), (String) body.get("messageType"),
            body.containsKey("relevanceScore") ? ((Number)body.get("relevanceScore")).doubleValue() : 0.0,
            body.containsKey("messageCount") ? ((Number)body.get("messageCount")).longValue() : 0L,
            (String) body.get("messageUuid"), (String) body.get("authorLabel"),
            (String) body.getOrDefault("authorKind","GUEST"), (String) body.get("detailVisible"),
            (String) body.get("receivedAt"), Boolean.TRUE.equals(body.get("wordVisible"))
        ));
        ok(res, cb, Map.of("status","updated"));
        return true;
    }
}
