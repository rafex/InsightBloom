package dev.rafex.insightbloom.query.adapters.inbound.http.handlers;
import dev.rafex.insightbloom.query.application.usecases.SetMessageVisibilityUseCase;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Callback;
import java.util.Map;
public class MessageVisibilityHandler extends BaseHandler {
    private final SetMessageVisibilityUseCase useCase;
    public MessageVisibilityHandler(SetMessageVisibilityUseCase useCase) { this.useCase = useCase; }
    @Override
    public boolean handle(Request req, Response res, Callback cb) throws Exception {
        if (!"POST".equals(req.getMethod())) { error(res, cb, 405, "method_not_allowed", "POST required"); return true; }
        var body = readBody(req, Map.class);
        String messageUuid = (String) body.get("messageUuid");
        boolean visible = Boolean.TRUE.equals(body.get("visible"));
        useCase.execute(new SetMessageVisibilityUseCase.Request(messageUuid, visible));
        ok(res, cb, Map.of("status", "updated"));
        return true;
    }
}
