package dev.rafex.insightbloom.moderation.adapters.inbound.http.handlers;
import dev.rafex.insightbloom.moderation.application.usecases.EvaluateCensureUseCase;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Callback;
import java.util.Map;
public class InternalEvaluateHandler extends BaseHandler {
    private final EvaluateCensureUseCase useCase;
    public InternalEvaluateHandler(EvaluateCensureUseCase useCase) { this.useCase = useCase; }
    @Override
    public boolean handle(Request request, Response response, Callback callback) throws Exception {
        if (!"POST".equals(request.getMethod())) {
            error(response, callback, 405, "method_not_allowed", "POST required"); return true;
        }
        var body = readBody(request, Map.class);
        var result = useCase.execute(new EvaluateCensureUseCase.EvaluateRequest(
            (String) body.get("word"), (String) body.get("detail")));
        ok(response, callback, result);
        return true;
    }
}
