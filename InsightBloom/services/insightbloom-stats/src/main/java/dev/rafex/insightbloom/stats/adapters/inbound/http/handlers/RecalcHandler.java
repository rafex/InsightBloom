package dev.rafex.insightbloom.stats.adapters.inbound.http.handlers;
import dev.rafex.insightbloom.stats.application.usecases.RecalcStatsUseCase;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Callback;
import java.util.Map;
public class RecalcHandler extends BaseHandler {
    private final RecalcStatsUseCase useCase;
    public RecalcHandler(RecalcStatsUseCase useCase) { this.useCase = useCase; }
    @Override
    public boolean handle(Request req, Response res, Callback cb) throws Exception {
        if (!"POST".equals(req.getMethod())) { error(res,cb,405,"method_not_allowed","POST required"); return true; }
        var body = readBody(req, Map.class);
        useCase.execute(new RecalcStatsUseCase.RecalcRequest(
            (String) body.get("conferenceUuid"), (String) body.get("wordNormalized"),
            (String) body.get("wordCanonical"), (String) body.get("messageType"),
            (String) body.get("wordIntent"), Boolean.TRUE.equals(body.get("visible"))
        ));
        ok(res, cb, Map.of("status","recalculated"));
        return true;
    }
}
