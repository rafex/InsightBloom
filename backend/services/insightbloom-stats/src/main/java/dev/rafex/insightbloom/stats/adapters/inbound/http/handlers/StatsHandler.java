package dev.rafex.insightbloom.stats.adapters.inbound.http.handlers;
import dev.rafex.insightbloom.stats.application.usecases.GetStatsUseCase;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Callback;
public class StatsHandler extends BaseHandler {
    private final GetStatsUseCase useCase;
    public StatsHandler(GetStatsUseCase useCase) { this.useCase = useCase; }
    @Override
    public boolean handle(Request req, Response res, Callback cb) throws Exception {
        if (!"GET".equals(req.getMethod())) { error(res,cb,405,"method_not_allowed","GET required"); return true; }
        String path = req.getHttpURI().getPath();
        String[] parts = path.split("/");
        String conferenceId = null;
        for (int i = 0; i < parts.length; i++) {
            if ("conferences".equals(parts[i]) && i+1 < parts.length) { conferenceId = parts[i+1]; break; }
        }
        if (conferenceId == null) { error(res,cb,400,"bad_request","conferenceId required"); return true; }
        if (path.endsWith("/relevance")) {
            String type = queryString(req, "type");
            ok(res, cb, useCase.relevance(conferenceId, type));
        } else {
            ok(res, cb, useCase.overview(conferenceId));
        }
        return true;
    }
}
