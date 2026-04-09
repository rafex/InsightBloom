package dev.rafex.insightbloom.query.adapters.inbound.http.handlers;
import dev.rafex.insightbloom.query.application.usecases.SetVisibilityUseCase;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Callback;
import java.util.Map;
public class VisibilityHandler extends BaseHandler {
    private final SetVisibilityUseCase useCase;
    public VisibilityHandler(SetVisibilityUseCase useCase) { this.useCase = useCase; }
    @Override
    public boolean handle(Request req, Response res, Callback cb) throws Exception {
        if (!"POST".equals(req.getMethod())) { error(res, cb, 405, "method_not_allowed", "POST required"); return true; }
        var body = readBody(req, Map.class);
        useCase.execute(new SetVisibilityUseCase.Request(
            (String) body.get("conferenceUuid"),
            (String) body.get("wordNormalized"),
            Boolean.TRUE.equals(body.get("visible"))
        ));
        ok(res, cb, Map.of("status", "updated"));
        return true;
    }
}
